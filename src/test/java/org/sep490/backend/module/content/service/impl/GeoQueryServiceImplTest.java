package org.sep490.backend.module.content.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.config.redis.RedisCircuitBreaker;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kiểm chứng cache cho hai truy vấn PostGIS.
 * Trọng tâm: LÀM TRÒN toạ độ — nếu không, mỗi lần gọi là một key mới và cache vô dụng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GeoQueryService - cache truy vấn PostGIS")
class GeoQueryServiceImplTest {

    @Mock private HotspotRepository hotspotRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;
    @Mock private RedisCircuitBreaker circuitBreaker;

    @InjectMocks private GeoQueryServiceImpl geoQueryService;

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
    }

    @Nested
    @DisplayName("isLocationInVietnam")
    class InVietnam {

        // UTCID01 - Normal: cache miss -> query DB rồi ghi cache với TTL dài
        @Test
        @DisplayName("Cache miss thì query DB rồi ghi cache với TTL dài")
        void missThiQueryDb() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(hotspotRepository.isLocationInVietnam(105.854, 21.028)).thenReturn(true);

            boolean result = geoQueryService.isLocationInVietnam(105.854, 21.028);

            assertThat(result).isTrue();
            verify(valueOps).set(anyString(), any(), any(Duration.class));
        }

        // UTCID02 - Normal: cache hit -> KHÔNG chạy ST_Within
        @Test
        @DisplayName("Cache hit thì KHÔNG chạy ST_Within")
        void hitThiKhongQueryDb() {
            when(valueOps.get(anyString())).thenReturn("true");

            boolean result = geoQueryService.isLocationInVietnam(105.854, 21.028);

            assertThat(result).isTrue();
            verify(hotspotRepository, never()).isLocationInVietnam(anyDouble(), anyDouble());
        }

        // UTCID03 - Abnormal: toạ độ null -> trả false, không gọi DB
        @Test
        @DisplayName("Toạ độ null trả false, không gọi DB")
        void toaDoNull() {
            assertThat(geoQueryService.isLocationInVietnam(null, 21.028)).isFalse();
            assertThat(geoQueryService.isLocationInVietnam(105.854, null)).isFalse();
            verify(hotspotRepository, never()).isLocationInVietnam(any(), any());
        }

        // UTCID04 - Boundary: toạ độ lệch <110m -> dùng CHUNG một key
        @Test
        @DisplayName("Toạ độ lệch <110m dùng CHUNG một key")
        void lamTronToaDo() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(hotspotRepository.isLocationInVietnam(anyDouble(), anyDouble())).thenReturn(true);

            geoQueryService.isLocationInVietnam(105.8541, 21.0281);
            geoQueryService.isLocationInVietnam(105.85449, 21.02849);

            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            verify(valueOps, org.mockito.Mockito.atLeastOnce())
                    .set(keys.capture(), any(), any(Duration.class));

            // Nếu không làm tròn, hai lời gọi sẽ sinh hai key khác nhau -> cache vô dụng
            assertThat(keys.getAllValues()).containsOnly("geo:vn:21.028:105.854");
        }

        // UTCID05 - Boundary: kinh độ 200.0 vượt biên -> ném lỗi
        @Test
        @DisplayName("Kinh độ 200.0 vượt biên -> ném lỗi thay vì trả false")
        void kinhDoNgoaiBienThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.isLocationInVietnam(200.0, 21.028));

            assertThat(ex.getMessage()).isEqualTo("Kinh độ phải nằm trong khoảng -180 đến 180");
            verify(hotspotRepository, never()).isLocationInVietnam(anyDouble(), anyDouble());
        }

        // UTCID06 - Boundary: vĩ độ 95.0 vượt biên -> ném lỗi
        @Test
        @DisplayName("Vĩ độ 95.0 vượt biên -> ném lỗi thay vì trả false")
        void viDoNgoaiBienThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.isLocationInVietnam(105.854, 95.0));

            assertThat(ex.getMessage()).isEqualTo("Vĩ độ phải nằm trong khoảng -90 đến 90");
            verify(hotspotRepository, never()).isLocationInVietnam(anyDouble(), anyDouble());
        }
    }

    @Nested
    @DisplayName("findNearby")
    class Nearby {

        private Hotspot hotspot(long id) {
            Hotspot h = new Hotspot();
            h.setHotspotId(id);
            return h;
        }

        // UTCID01 - Normal: cache miss -> chạy ST_DWithin và lưu danh sách ID
        @Test
        @DisplayName("Cache miss thì chạy ST_DWithin và lưu danh sách ID")
        void missThiQueryDb() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(hotspotRepository.findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hotspot(1L), hotspot(2L)));

            List<Hotspot> result = geoQueryService.findNearby(105.854, 21.028, 3000, "PUBLISHED");

            assertThat(result).hasSize(2);
            // Cache lưu ID, KHÔNG lưu entity JPA (tránh lazy association)
            verify(valueOps).set(anyString(), any(List.class), any(Duration.class));
        }

        // UTCID02 - Normal: cache hit -> nạp theo ID, KHÔNG chạy ST_DWithin
        @Test
        @DisplayName("Cache hit thì nạp theo ID, KHÔNG chạy ST_DWithin")
        void hitThiKhongQueryPostGIS() {
            when(valueOps.get(anyString())).thenReturn(List.of("1", "2"));
            when(hotspotRepository.findAllById(any()))
                    .thenReturn(List.of(hotspot(1L), hotspot(2L)));

            List<Hotspot> result = geoQueryService.findNearby(105.854, 21.028, 3000, "PUBLISHED");

            assertThat(result).hasSize(2);
            verify(hotspotRepository, never())
                    .findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString());
        }

        // UTCID03 - Abnormal: có hotspot đã bị xoá -> bỏ cache, truy vấn lại DB
        @Test
        @DisplayName("Có hotspot đã bị xoá thì bỏ cache, truy vấn lại DB")
        void hotspotBiXoaThiQueryLai() {
            when(valueOps.get(anyString())).thenReturn(List.of("1", "2", "3"));
            // Chỉ còn 2/3 -> cache đã cũ
            when(hotspotRepository.findAllById(any()))
                    .thenReturn(List.of(hotspot(1L), hotspot(2L)));
            when(hotspotRepository.findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hotspot(1L)));

            List<Hotspot> result = geoQueryService.findNearby(105.854, 21.028, 3000, "PUBLISHED");

            assertThat(result).hasSize(1);
            verify(hotspotRepository)
                    .findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString());
        }

        // UTCID04 - Normal: giữ đúng thứ tự gần -> xa
        @Test
        @DisplayName("Giữ đúng thứ tự gần -> xa dù findAllById trả lộn xộn")
        void giuDungThuTu() {
            when(valueOps.get(anyString())).thenReturn(List.of("3", "1", "2"));
            // findAllById KHÔNG đảm bảo thứ tự
            when(hotspotRepository.findAllById(any()))
                    .thenReturn(List.of(hotspot(1L), hotspot(2L), hotspot(3L)));

            List<Hotspot> result = geoQueryService.findNearby(105.854, 21.028, 3000, "PUBLISHED");

            assertThat(result).extracting(Hotspot::getHotspotId)
                    .containsExactly(3L, 1L, 2L);
        }

        // UTCID05 - Boundary: radius khác nhau -> dùng key khác nhau
        @Test
        @DisplayName("Radius khác nhau dùng key khác nhau")
        void radiusKhacThiKeyKhac() {
            when(valueOps.get(anyString())).thenReturn(null);
            when(hotspotRepository.findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString()))
                    .thenReturn(List.of(hotspot(1L)));

            geoQueryService.findNearby(105.854, 21.028, 3000, "PUBLISHED");
            geoQueryService.findNearby(105.854, 21.028, 5000, "PUBLISHED");

            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            verify(valueOps, org.mockito.Mockito.atLeast(2))
                    .set(keys.capture(), any(), any(Duration.class));

            assertThat(keys.getAllValues()).contains(
                    "geo:nearby:21.028:105.854:3000:PUBLISHED",
                    "geo:nearby:21.028:105.854:5000:PUBLISHED");
        }

        // UTCID06 - Boundary: kinh độ 181.0 vượt biên -> ném lỗi
        @Test
        @DisplayName("Kinh độ 181.0 vượt biên -> ném lỗi, không chạm Redis")
        void kinhDoNgoaiBienThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.findNearby(181.0, 21.028, 3000, "PUBLISHED"));

            assertThat(ex.getMessage()).isEqualTo("Kinh độ phải nằm trong khoảng -180 đến 180");
            verify(hotspotRepository, never())
                    .findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString());
        }

        // UTCID07 - Boundary: vĩ độ -90.5 vượt biên -> ném lỗi
        @Test
        @DisplayName("Vĩ độ -90.5 vượt biên -> ném lỗi, không chạm Redis")
        void viDoNgoaiBienThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.findNearby(105.854, -90.5, 3000, "PUBLISHED"));

            assertThat(ex.getMessage()).isEqualTo("Vĩ độ phải nằm trong khoảng -90 đến 90");
            verify(hotspotRepository, never())
                    .findNearbyHotspotsWithStatus(anyDouble(), anyDouble(), anyDouble(), anyString());
        }

        // UTCID08 - Boundary: bán kính 0 -> phải lớn hơn 0
        @Test
        @DisplayName("Bán kính 0 -> ném lỗi bán kính phải lớn hơn 0")
        void banKinhBangKhongThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.findNearby(105.854, 21.028, 0, "PUBLISHED"));

            assertThat(ex.getMessage()).isEqualTo("Bán kính tìm kiếm phải lớn hơn 0");
        }

        // UTCID09 - Boundary: bán kính 50001m -> vượt trần 50km
        @Test
        @DisplayName("Bán kính 50001m vượt trần 50km -> ném lỗi")
        void banKinhVuotTranThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.findNearby(105.854, 21.028, 50001, "PUBLISHED"));

            assertThat(ex.getMessage()).isEqualTo("Bán kính tìm kiếm không được vượt quá 50000 mét");
        }

        // UTCID10 - Abnormal: status rỗng -> không được để trống
        @Test
        @DisplayName("Status rỗng -> ném lỗi trạng thái không được để trống")
        void statusRongThiNemLoi() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> geoQueryService.findNearby(105.854, 21.028, 3000, "  "));

            assertThat(ex.getMessage()).isEqualTo("Trạng thái địa điểm không được để trống");
        }
    }
}
