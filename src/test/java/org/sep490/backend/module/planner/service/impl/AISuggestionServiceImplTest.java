package org.sep490.backend.module.planner.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.common.utils.SpatialUtils;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.service.inter.CheckInStatusService;
import org.sep490.backend.module.content.service.inter.GeoQueryService;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.content.service.inter.RatingSummaryService;
import org.sep490.backend.module.planner.dto.record.HotspotPick;
import org.sep490.backend.module.planner.dto.record.HotspotPickList;
import org.sep490.backend.module.planner.dto.request.DescriptionSuggestRequest;
import org.sep490.backend.module.planner.dto.request.NearbySuggestRequest;
import org.sep490.backend.module.planner.dto.response.HotspotSuggestionResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho GỢI Ý ĐỊA ĐIỂM BẰNG AI (lọc ứng viên, xếp hạng theo LLM, gợi ý lân cận).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AISuggestionServiceImplTest {

    @Mock private ChatClient chatClient;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private StoryRepository storyRepository;
    @Mock private HotspotService hotspotService;
    @Mock private HotspotMapper hotspotMapper;
    @Mock private StoryMapper storyMapper;
    @Mock private RatingSummaryService ratingSummaryService;
    @Mock private CheckInStatusService checkInStatusService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RedisCircuitBreaker circuitBreaker;
    @Mock private GeoQueryService geoQueryService;

    @InjectMocks private AISuggestionServiceImpl aiSuggestionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());

        when(hotspotMapper.toResponse(any(Hotspot.class))).thenAnswer(inv -> {
            HotspotResponse response = new HotspotResponse();
            response.setHotspotId(((Hotspot) inv.getArgument(0)).getHotspotId());
            return response;
        });
        when(storyRepository.findByHotspotOrderedByIndex(anyLong())).thenReturn(List.of());
        when(ratingSummaryService.applyToHotspot(any(HotspotResponse.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Toạ độ thật ở Hà Nội để khoảng cách Haversine ra số có nghĩa. */
    private static Hotspot hotspot(Long id, String name, double lon, double lat, ContentStatus status) {
        Hotspot hotspot = new Hotspot();
        hotspot.setHotspotId(id);
        hotspot.setHotspotName(name);
        hotspot.setLocation(SpatialUtils.fromCoordinates(lon, lat));
        hotspot.setStatus(status);
        return hotspot;
    }

    private static Hotspot vanMieu() {
        return hotspot(1L, "Văn Miếu Quốc Tử Giám", 105.8355, 21.0278, ContentStatus.PUBLISHED);
    }

    private static Hotspot hoGuom() {
        return hotspot(2L, "Hồ Hoàn Kiếm", 105.8523, 21.0287, ContentStatus.PUBLISHED);
    }

    private static Hotspot chuaMotCot() {
        return hotspot(3L, "Chùa Một Cột", 105.8342, 21.0359, ContentStatus.PUBLISHED);
    }

    private static DescriptionSuggestRequest descriptionRequest(String description) {
        DescriptionSuggestRequest request = new DescriptionSuggestRequest();
        request.setDescription(description);
        return request;
    }

    private static NearbySuggestRequest nearbyRequest(List<Long> anchors, Double radius, Integer limit) {
        NearbySuggestRequest request = new NearbySuggestRequest();
        request.setAnchorHotspotIds(anchors);
        request.setRadiusInMeters(radius);
        request.setLimit(limit);
        return request;
    }

    // =====================================================================
    // Function: suggestByDescription
    // =====================================================================
    @Nested
    @DisplayName("suggestByDescription")
    class SuggestByDescriptionTest {

        // UTCID01 - Boundary: không có địa điểm PUBLISHED nào -> trả danh sách rỗng, không gọi LLM
        @Test
        void suggestByDescription_noCandidates_returnsEmptyWithoutLlm() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED)).thenReturn(List.of());

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestByDescription(descriptionRequest("nơi yên tĩnh có lịch sử"));

            assertTrue(result.isEmpty());
            verifyNoInteractions(chatClient);
        }

        // UTCID02 - Normal: LLM (từ cache) xếp hạng 2 địa điểm -> trả đúng điểm và lý do
        @Test
        void suggestByDescription_llmPicks_returnsScoredSuggestions() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED))
                    .thenReturn(List.of(vanMieu(), hoGuom()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(1L, 0.9, "Di tích lịch sử yên tĩnh"),
                    new HotspotPick(2L, 0.6, "Hồ trung tâm, khá đông"))));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestByDescription(descriptionRequest("nơi yên tĩnh có lịch sử"));

            assertEquals(2, result.size());
            assertEquals(1L, result.get(0).getHotspot().getHotspotId());
            assertEquals(0.9, result.get(0).getScore());
            assertEquals("Di tích lịch sử yên tĩnh", result.get(0).getReason());
        }

        // UTCID03 - Normal: kết quả LLM trả lộn xộn -> sắp xếp lại theo điểm giảm dần
        @Test
        void suggestByDescription_unorderedPicks_sortedByScoreDesc() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED))
                    .thenReturn(List.of(vanMieu(), hoGuom(), chuaMotCot()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(2L, 0.4, "Ít phù hợp"),
                    new HotspotPick(1L, 0.95, "Rất phù hợp"),
                    new HotspotPick(3L, 0.7, "Khá phù hợp"))));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestByDescription(descriptionRequest("nơi yên tĩnh có lịch sử"));

            assertEquals(List.of(1L, 3L, 2L),
                    result.stream().map(r -> r.getHotspot().getHotspotId()).toList());
        }

        // UTCID04 - Boundary: limit = 2 -> chỉ lấy 2 kết quả điểm cao nhất
        @Test
        void suggestByDescription_withLimit_truncatesResult() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED))
                    .thenReturn(List.of(vanMieu(), hoGuom(), chuaMotCot()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(1L, 0.95, "Rất phù hợp"),
                    new HotspotPick(3L, 0.7, "Khá phù hợp"),
                    new HotspotPick(2L, 0.4, "Ít phù hợp"))));

            DescriptionSuggestRequest request = descriptionRequest("nơi yên tĩnh có lịch sử");
            request.setLimit(2);

            assertEquals(2, aiSuggestionService.suggestByDescription(request).size());
        }

        // UTCID05 - Abnormal: LLM trả id không có trong danh sách ứng viên -> bỏ qua id bịa
        @Test
        void suggestByDescription_llmHallucinatesId_ignoresUnknownId() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED))
                    .thenReturn(List.of(vanMieu()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(1L, 0.9, "Phù hợp"),
                    new HotspotPick(999L, 0.99, "Địa điểm không tồn tại"))));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestByDescription(descriptionRequest("nơi yên tĩnh có lịch sử"));

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getHotspot().getHotspotId());
        }

        // UTCID06 - Normal: có toạ độ người dùng -> tính khoảng cách tới từng gợi ý
        @Test
        void suggestByDescription_withUserLocation_computesDistance() {
            when(hotspotRepository.findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hoGuom()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(2L, 0.8, "Gần bạn"))));

            DescriptionSuggestRequest request = descriptionRequest("quán cà phê view hồ");
            request.setLatitude(21.0278);
            request.setLongitude(105.8355);

            List<HotspotSuggestionResponse> result = aiSuggestionService.suggestByDescription(request);

            assertEquals(1, result.size());
            assertNotNull(result.get(0).getDistanceInMeters());
            assertTrue(result.get(0).getDistanceInMeters() > 0);
        }

        // UTCID07 - Boundary: không có toạ độ và không có địa điểm neo -> khoảng cách là null
        @Test
        void suggestByDescription_withoutOrigin_distanceIsNull() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED)).thenReturn(List.of(vanMieu()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(1L, 0.9, "Phù hợp"))));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestByDescription(descriptionRequest("nơi yên tĩnh có lịch sử"));

            assertNull(result.get(0).getDistanceInMeters());
        }

        // UTCID08 - Boundary: có địa điểm neo -> chính địa điểm neo bị loại khỏi kết quả gợi ý
        @Test
        void suggestByDescription_withAnchors_excludesAnchorsFromCandidates() {
            Hotspot anchor = vanMieu();
            when(hotspotService.getById(1L)).thenReturn(anchor);
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(anchor, hoGuom()));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(1L, 0.99, "Chính nó"),
                    new HotspotPick(2L, 0.8, "Gần đó"))));

            DescriptionSuggestRequest request = descriptionRequest("thêm điểm gần đây");
            request.setAnchorHotspotIds(List.of(1L));

            List<HotspotSuggestionResponse> result = aiSuggestionService.suggestByDescription(request);

            assertEquals(1, result.size());
            assertEquals(2L, result.get(0).getHotspot().getHotspotId());
        }

        // UTCID09 - Boundary: địa điểm chưa PUBLISHED bị lọc khỏi ứng viên
        @Test
        void suggestByDescription_unpublishedHotspot_isFilteredOut() {
            when(hotspotRepository.findByStatus(ContentStatus.PUBLISHED)).thenReturn(List.of(
                    vanMieu(),
                    hotspot(4L, "Địa điểm nháp", 105.84, 21.03, ContentStatus.DRAFT)));
            when(valueOps.get(anyString())).thenReturn(new HotspotPickList(List.of(
                    new HotspotPick(1L, 0.9, "Phù hợp"),
                    new HotspotPick(4L, 0.95, "Chưa duyệt"))));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestByDescription(descriptionRequest("nơi yên tĩnh"));

            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getHotspot().getHotspotId());
        }
    }

    // =====================================================================
    // Function: suggestNearby
    // =====================================================================
    @Nested
    @DisplayName("suggestNearby")
    class SuggestNearbyTest {

        // UTCID01 - Normal: 1 địa điểm neo -> trả các địa điểm quanh đó kèm khoảng cách
        @Test
        void suggestNearby_singleAnchor_returnsNearbyWithDistance() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hoGuom(), chuaMotCot()));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L), null, null));

            assertEquals(2, result.size());
            assertTrue(result.get(0).getDistanceInMeters() > 0);
        }

        // UTCID02 - Normal: kết quả sắp xếp theo khoảng cách tăng dần (gần nhất trước)
        @Test
        void suggestNearby_multipleResults_sortedByDistanceAsc() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hoGuom(), chuaMotCot()));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L), null, null));

            assertTrue(result.get(0).getDistanceInMeters() <= result.get(1).getDistanceInMeters());
        }

        // UTCID03 - Boundary: địa điểm neo không xuất hiện trong chính kết quả gợi ý
        @Test
        void suggestNearby_anchorItself_isExcluded() {
            Hotspot anchor = vanMieu();
            when(hotspotService.getById(1L)).thenReturn(anchor);
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(anchor, hoGuom()));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L), null, null));

            assertEquals(1, result.size());
            assertEquals(2L, result.get(0).getHotspot().getHotspotId());
        }

        // UTCID04 - Boundary: địa điểm chưa PUBLISHED bị loại khỏi gợi ý
        @Test
        void suggestNearby_unpublishedHotspot_isExcluded() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hoGuom(),
                            hotspot(4L, "Địa điểm nháp", 105.84, 21.03, ContentStatus.DRAFT)));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L), null, null));

            assertEquals(1, result.size());
            assertEquals(2L, result.get(0).getHotspot().getHotspotId());
        }

        // UTCID05 - Boundary: limit = 1 -> chỉ lấy địa điểm gần nhất
        @Test
        void suggestNearby_withLimit_truncatesResult() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hoGuom(), chuaMotCot()));

            assertEquals(1, aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L), null, 1)).size());
        }

        // UTCID06 - Boundary: 2 neo cùng trả 1 địa điểm -> chỉ xuất hiện 1 lần, lấy khoảng cách nhỏ nhất
        @Test
        void suggestNearby_duplicateFromTwoAnchors_keepsNearestOnly() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(hotspotService.getById(3L)).thenReturn(chuaMotCot());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hoGuom()));

            List<HotspotSuggestionResponse> result = aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L, 3L), null, null));

            assertEquals(1, result.size());
            double fromVanMieu = SpatialUtils.calculateDistanceInMeters(
                    vanMieu().getLocation(), hoGuom().getLocation());
            double fromChua = SpatialUtils.calculateDistanceInMeters(
                    chuaMotCot().getLocation(), hoGuom().getLocation());
            assertEquals(Math.min(fromVanMieu, fromChua), result.get(0).getDistanceInMeters(), 0.001);
        }

        // UTCID07 - Boundary: quanh địa điểm neo không có gì -> trả danh sách rỗng
        @Test
        void suggestNearby_noNeighbours_returnsEmpty() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of());

            assertTrue(aiSuggestionService
                    .suggestNearby(nearbyRequest(List.of(1L), null, null)).isEmpty());
        }

        // UTCID08 - Normal: truyền bán kính tùy chỉnh 1000m -> dùng đúng bán kính đó khi truy vấn
        @Test
        void suggestNearby_customRadius_isPassedToGeoQuery() {
            when(hotspotService.getById(1L)).thenReturn(vanMieu());
            when(geoQueryService.findNearby(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of());

            aiSuggestionService.suggestNearby(nearbyRequest(List.of(1L), 1000.0, null));

            verify(geoQueryService).findNearby(anyDouble(), anyDouble(), eq(1000.0),
                    eq(ContentStatus.PUBLISHED.name()));
        }

        // UTCID09 - Abnormal: request = null -> chặn ngay đầu vào
        @Test
        void suggestNearby_nullRequest_throwsEmptyRequest() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiSuggestionService.suggestNearby(null));

            assertEquals("Yêu cầu gợi ý không được để trống", ex.getMessage());
            verifyNoInteractions(geoQueryService);
        }

        // UTCID10 - Abnormal: anchorHotspotIds = null -> chưa chọn địa điểm neo nào
        @Test
        void suggestNearby_nullAnchors_throwsAnchorRequired() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiSuggestionService.suggestNearby(nearbyRequest(null, null, null)));

            assertEquals("Cần chọn ít nhất một địa điểm neo", ex.getMessage());
            verifyNoInteractions(geoQueryService);
        }

        // UTCID11 - Boundary: anchorHotspotIds = [] -> chưa chọn địa điểm neo nào
        @Test
        void suggestNearby_emptyAnchors_throwsAnchorRequired() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiSuggestionService.suggestNearby(nearbyRequest(List.of(), null, null)));

            assertEquals("Cần chọn ít nhất một địa điểm neo", ex.getMessage());
            verifyNoInteractions(geoQueryService);
        }

        // UTCID12 - Boundary: 11 địa điểm neo -> vượt trần 10 (mỗi neo là 1 vòng PostGIS)
        @Test
        void suggestNearby_tooManyAnchors_throwsAnchorLimitExceeded() {
            List<Long> anchors = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiSuggestionService.suggestNearby(nearbyRequest(anchors, null, null)));

            assertEquals("Không được chọn quá 10 địa điểm neo", ex.getMessage());
            verifyNoInteractions(geoQueryService);
        }

        // UTCID13 - Boundary: radiusInMeters = 0 -> bán kính phải lớn hơn 0
        @Test
        void suggestNearby_zeroRadius_throwsInvalidRadius() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiSuggestionService.suggestNearby(nearbyRequest(List.of(1L), 0.0, null)));

            assertEquals("Bán kính tìm kiếm phải lớn hơn 0", ex.getMessage());
            verifyNoInteractions(geoQueryService);
        }

        // UTCID14 - Boundary: limit = 0 -> số lượng gợi ý phải lớn hơn 0
        @Test
        void suggestNearby_zeroLimit_throwsInvalidLimit() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> aiSuggestionService.suggestNearby(nearbyRequest(List.of(1L), null, 0)));

            assertEquals("Số lượng gợi ý phải lớn hơn 0", ex.getMessage());
            verifyNoInteractions(geoQueryService);
        }
    }
}
