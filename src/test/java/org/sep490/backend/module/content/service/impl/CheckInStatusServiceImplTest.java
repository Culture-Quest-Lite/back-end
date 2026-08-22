package org.sep490.backend.module.content.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.utils.SecurityUtils;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.dto.response.HotspotResponse;
import org.sep490.backend.module.exploration.repository.UserHotspotProgressRepository;
import org.sep490.backend.module.user.service.UserService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho TRẠNG THÁI ĐÃ CHECK-IN gắn vào danh sách địa điểm (đọc Redis, fallback DB).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInStatusServiceImplTest {

    @Mock private UserHotspotProgressRepository userHotspotProgressRepository;
    @Mock private UserService userService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private SetOperations<String, Object> setOps;
    @Mock private RedisCircuitBreaker circuitBreaker;

    @InjectMocks private CheckInStatusServiceImpl checkInStatusService;

    private MockedStatic<SecurityUtils> securityUtils;

    @BeforeEach
    void setUp() {
        securityUtils = mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.of("kc-001"));

        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(circuitBreaker.read(anyString(), any(), any())).thenAnswer(inv -> {
            Supplier<?> supplier = inv.getArgument(1);
            return supplier.get();
        });
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(1);
            runnable.run();
            return null;
        }).when(circuitBreaker).write(anyString(), any());

        User current = new User();
        current.setUserId(1L);
        when(userService.getCurrentUser()).thenReturn(current);
    }

    @AfterEach
    void tearDown() {
        securityUtils.close();
    }

    private static HotspotResponse hotspot(Long hotspotId, String name) {
        HotspotResponse response = new HotspotResponse();
        response.setHotspotId(hotspotId);
        response.setHotspotName(name);
        return response;
    }

    // =====================================================================
    // Function: apply (danh sách)
    // =====================================================================
    @Nested
    @DisplayName("apply")
    class ApplyTest {

        // UTCID01 - Boundary: danh sách null -> thoát sớm, không gọi Redis/DB
        @Test
        void apply_nullList_returnsWithoutQuery() {
            assertDoesNotThrow(() -> checkInStatusService.apply((List<HotspotResponse>) null));

            verifyNoInteractions(userHotspotProgressRepository);
            verifyNoInteractions(userService);
        }

        // UTCID02 - Boundary: danh sách rỗng -> thoát sớm
        @Test
        void apply_emptyList_returnsWithoutQuery() {
            checkInStatusService.apply(new ArrayList<HotspotResponse>());

            verifyNoInteractions(userHotspotProgressRepository);
        }

        // UTCID03 - Abnormal: khách chưa đăng nhập -> tất cả địa điểm đều isCheckIn = false
        @Test
        void apply_anonymousUser_setsAllFalse() {
            securityUtils.when(SecurityUtils::getCurrentUserKeyCloakId).thenReturn(Optional.empty());
            List<HotspotResponse> responses = new ArrayList<>(List.of(
                    hotspot(1L, "Văn Miếu"), hotspot(2L, "Hồ Gươm")));

            checkInStatusService.apply(responses);

            assertFalse(responses.get(0).getIsCheckIn());
            assertFalse(responses.get(1).getIsCheckIn());
            verifyNoInteractions(userHotspotProgressRepository);
        }

        // UTCID04 - Normal: Redis có sẵn danh sách -> đánh dấu đúng, KHÔNG truy vấn DB
        @Test
        void apply_cacheHit_marksCheckedInWithoutDbQuery() {
            when(setOps.members("checkin:user:1")).thenReturn(Set.of("1", "3"));
            List<HotspotResponse> responses = new ArrayList<>(List.of(
                    hotspot(1L, "Văn Miếu"), hotspot(2L, "Hồ Gươm"), hotspot(3L, "Chùa Một Cột")));

            checkInStatusService.apply(responses);

            assertTrue(responses.get(0).getIsCheckIn());
            assertFalse(responses.get(1).getIsCheckIn());
            assertTrue(responses.get(2).getIsCheckIn());
            verify(userHotspotProgressRepository, never()).findAllCheckedInHotspotIds(anyLong());
        }

        // UTCID05 - Normal: Redis trống -> đọc DB rồi ghi ngược lại cache kèm TTL 1 giờ
        @Test
        void apply_cacheMiss_loadsFromDbAndWarmsCache() {
            when(setOps.members("checkin:user:1")).thenReturn(Set.of());
            when(userHotspotProgressRepository.findAllCheckedInHotspotIds(1L))
                    .thenReturn(List.of(2L));
            List<HotspotResponse> responses = new ArrayList<>(List.of(
                    hotspot(1L, "Văn Miếu"), hotspot(2L, "Hồ Gươm")));

            checkInStatusService.apply(responses);

            assertFalse(responses.get(0).getIsCheckIn());
            assertTrue(responses.get(1).getIsCheckIn());
            verify(setOps).add(eq("checkin:user:1"), any(Object[].class));
            verify(redisTemplate).expire("checkin:user:1", Duration.ofHours(1));
        }

        // UTCID06 - Boundary: user chưa check-in địa điểm nào -> không ghi cache rỗng
        @Test
        void apply_noCheckInHistory_doesNotWriteEmptyCache() {
            when(setOps.members("checkin:user:1")).thenReturn(Set.of());
            when(userHotspotProgressRepository.findAllCheckedInHotspotIds(1L)).thenReturn(List.of());
            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L, "Văn Miếu")));

            checkInStatusService.apply(responses);

            assertFalse(responses.get(0).getIsCheckIn());
            verify(setOps, never()).add(anyString(), any(Object[].class));
        }

        // UTCID07 - Boundary: có phần tử thiếu hotspotId -> bỏ qua khi truy vấn, gán false cho nó
        @Test
        void apply_responseWithNullHotspotId_isMarkedFalse() {
            when(setOps.members("checkin:user:1")).thenReturn(Set.of("1"));
            List<HotspotResponse> responses = new ArrayList<>(Arrays.asList(
                    hotspot(1L, "Văn Miếu"), hotspot(null, "Địa điểm chưa lưu")));

            checkInStatusService.apply(responses);

            assertTrue(responses.get(0).getIsCheckIn());
            assertFalse(responses.get(1).getIsCheckIn());
        }

        // UTCID08 - Normal: apply cho 1 địa điểm -> trả về chính object đã được gán trạng thái
        @Test
        void apply_singleResponse_returnsSameObjectMarked() {
            when(setOps.members("checkin:user:1")).thenReturn(Set.of("1"));
            HotspotResponse response = hotspot(1L, "Văn Miếu");

            HotspotResponse result = checkInStatusService.apply(response);

            assertSame(response, result);
            assertTrue(result.getIsCheckIn());
        }

        // UTCID09 - Abnormal: Redis chết (circuit breaker mở) -> fallback DB, không ném lỗi
        @Test
        void apply_redisDown_fallsBackToDatabase() {
            doReturn(null).when(circuitBreaker).read(anyString(), any(), any());
            when(userHotspotProgressRepository.findAllCheckedInHotspotIds(1L)).thenReturn(List.of(1L));
            List<HotspotResponse> responses = new ArrayList<>(List.of(hotspot(1L, "Văn Miếu")));

            assertDoesNotThrow(() -> checkInStatusService.apply(responses));
            assertTrue(responses.get(0).getIsCheckIn());
        }
    }

    // =====================================================================
    // Function: addCheckedIn
    // =====================================================================
    @Nested
    @DisplayName("addCheckedIn")
    class AddCheckedInTest {

        // UTCID01 - Abnormal: userId null -> bỏ qua, không đụng Redis
        @Test
        void addCheckedIn_nullUserId_doesNothing() {
            checkInStatusService.addCheckedIn(null, 5L);

            verifyNoInteractions(circuitBreaker);
        }

        // UTCID02 - Abnormal: hotspotId null -> bỏ qua, không đụng Redis
        @Test
        void addCheckedIn_nullHotspotId_doesNothing() {
            checkInStatusService.addCheckedIn(1L, null);

            verifyNoInteractions(circuitBreaker);
        }

        // UTCID03 - Normal: cache đang tồn tại -> thêm hotspot mới và gia hạn TTL
        @Test
        void addCheckedIn_existingCache_addsAndRefreshesTtl() {
            when(redisTemplate.hasKey("checkin:user:1")).thenReturn(true);

            checkInStatusService.addCheckedIn(1L, 5L);

            verify(setOps).add("checkin:user:1", "5");
            verify(redisTemplate).expire("checkin:user:1", Duration.ofHours(1));
        }

        // UTCID04 - Boundary: chưa có cache -> KHÔNG tạo cache thiếu dữ liệu (tránh sai kết quả)
        @Test
        void addCheckedIn_noExistingCache_doesNotCreatePartialCache() {
            when(redisTemplate.hasKey("checkin:user:1")).thenReturn(false);

            checkInStatusService.addCheckedIn(1L, 5L);

            verify(setOps, never()).add(anyString(), any(Object.class));
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        // UTCID05 - Abnormal: hasKey trả null (Redis lỗi) -> coi như chưa có cache, không ghi
        @Test
        void addCheckedIn_hasKeyReturnsNull_doesNotWrite() {
            when(redisTemplate.hasKey("checkin:user:1")).thenReturn(null);

            assertDoesNotThrow(() -> checkInStatusService.addCheckedIn(1L, 5L));

            verify(setOps, never()).add(anyString(), any(Object.class));
        }
    }
}
