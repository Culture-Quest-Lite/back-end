package org.sep490.backend.module.content.service.impl;

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
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.request.RouteRequest;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.content.dto.response.RouteResponse;
import org.sep490.backend.module.content.dto.response.StoryResponse;
import org.sep490.backend.module.content.dto.response.TagResponse;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Route;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.Tag;
import org.sep490.backend.module.content.entity.enumeration.RouteStatus;
import org.sep490.backend.module.content.mapper.HotspotMapper;
import org.sep490.backend.module.content.mapper.RouteMapper;
import org.sep490.backend.module.content.mapper.StoryMapper;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.content.repository.TagRepository;
import org.sep490.backend.module.content.service.inter.ImageService;
import org.sep490.backend.module.content.service.inter.HotspotService;
import org.sep490.backend.module.user.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho luồng QUẢN LÝ ROUTE (Content).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteServiceImplTest {

    @Mock private RouteRepository routeRepository;
    @Mock private RouteMapper routeMapper;
    @Mock private StoryRepository storyRepository;
    @Mock private HotspotRepository hotspotRepository;
    @Mock private HotspotService hotspotService;
    @Mock private UserService userService;
    @Mock private ImageService imageService;
    @Mock private StoryMapper storyMapper;
    @Mock private TagRepository tagRepository;
    @Mock private HotspotMapper hotspotMapper;

    @InjectMocks private RouteServiceImpl routeService;

    // =====================================================================
    // Function: create (Route)
    // =====================================================================
    @Nested
    @DisplayName("createRoute")
    class CreateRouteTest {

        private RouteRequest routeRequest(List<Long> hotspotIds) {
            RouteRequest request = new RouteRequest();
            request.setRouteName("Phố cổ Hà Nội");
            request.setTagId(1L);
            request.setHotspotIds(hotspotIds);
            return request;
        }

        // UTCID01 - Abnormal: ít hơn 4 điểm dừng
        @Test
        void createRoute_lessThanFourStops_throwsMinStops() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.create(request));

            assertEquals("Tuyến đường phải có ít nhất 4 điểm dừng (Hotspot)", ex.getMessage());
        }

        // UTCID02 - Abnormal: tag không tồn tại
        @Test
        void createRoute_tagNotFound_throwsTagNotExist() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            when(tagRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.create(request));

            assertEquals("Tag không tồn tại với ID: 1", ex.getMessage());
        }

        // UTCID03 - Abnormal: một hotspot chưa có cốt truyện nào
        @Test
        void createRoute_hotspotWithoutStory_throwsNoStory() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            Tag tag = new Tag();
            tag.setTagId(1L);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            when(routeMapper.toEntity(request)).thenReturn(new Route());
            when(userService.getCurrentUser()).thenReturn(new User());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(10L);
            hotspot.setHotspotName("Hồ Gươm");
            when(hotspotService.getById(10L)).thenReturn(hotspot);
            when(storyRepository.findByHotspotOrderedByIndex(10L)).thenReturn(List.of());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> routeService.create(request));

            assertEquals("Địa điểm Hồ Gươm chưa có cốt truyện nào!", ex.getMessage());
        }

        // UTCID04 - Normal: tạo tuyến đường thành công, trạng thái DRAFT
        @Test
        void createRoute_valid_createsDraftRoute() {
            RouteRequest request = routeRequest(List.of(10L, 11L, 12L, 13L));
            Tag tag = new Tag();
            tag.setTagId(1L);
            when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
            Route route = new Route();
            when(routeMapper.toEntity(request)).thenReturn(route);
            when(userService.getCurrentUser()).thenReturn(new User());
            when(routeRepository.save(any(Route.class))).thenAnswer(inv -> inv.getArgument(0));

            Hotspot hotspot = new Hotspot();
            hotspot.setHotspotId(10L);
            hotspot.setHotspotName("Hồ Gươm");
            hotspot.setLocation(SpatialUtils.fromCoordinates(105.85, 21.02));
            Story story = new Story();
            story.setHotspot(hotspot);
            when(hotspotService.getById(anyLong())).thenReturn(hotspot);
            when(storyRepository.findByHotspotOrderedByIndex(anyLong())).thenReturn(List.of(story));
            when(storyRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(storyRepository.findByHotspotOrderedByRouteTag(anyLong(), anyList())).thenReturn(List.of());
            when(routeMapper.toResponse(any(Route.class))).thenReturn(new RouteResponse());
            when(hotspotMapper.toResponse(any(Hotspot.class))).thenReturn(new HotspotResponse());
            when(storyMapper.toResponse(any(Story.class))).thenReturn(new StoryResponse());
            when(storyMapper.toTagResponse(any(Tag.class))).thenReturn(new TagResponse());

            RouteResponse response = routeService.create(request);

            assertNotNull(response);
            assertEquals(RouteStatus.DRAFT, route.getStatus());
            assertEquals(4, route.getTotalStops());
            verify(routeRepository).save(route);
        }
    }
}
