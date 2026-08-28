package org.sep490.backend.module.authorization.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.repository.HotspotRepository;
import org.sep490.backend.module.content.repository.ReviewRepository;
import org.sep490.backend.module.content.repository.RouteRepository;
import org.sep490.backend.module.content.repository.StoryRepository;
import org.sep490.backend.module.exploration.repository.SavedRouteRepository;
import org.sep490.backend.module.groupquest.entity.enumuration.GroupRole;
import org.sep490.backend.module.groupquest.repository.GroupParticipantRepository;
import org.sep490.backend.module.planner.repository.UserPlanRepository;
import org.sep490.backend.module.social.repository.PostRepository;
import org.sep490.backend.module.user.service.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OwnershipService — kiểm tra chủ sở hữu tài nguyên")
class OwnershipServiceImplTest {

    @Mock UserService userService;
    @Mock PostRepository postRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock RouteRepository routeRepository;
    @Mock HotspotRepository hotspotRepository;
    @Mock StoryRepository storyRepository;
    @Mock UserPlanRepository userPlanRepository;
    @Mock SavedRouteRepository savedRouteRepository;
    @Mock GroupParticipantRepository groupParticipantRepository;

    @InjectMocks OwnershipServiceImpl ownershipService;

    @BeforeEach
    void setUp() {
        // @PostConstruct không tự chạy trong unit test -> phải gọi tay
        ownershipService.init();
    }

    private void currentUserIs(Long userId) {
        when(userService.getCurrentUser()).thenReturn(User.builder().userId(userId).build());
    }

    // ===== isOwner =====

    /**
     * REGRESSION TEST cho bug thật ở SaveRouteServiceImpl:57.
     *
     * Java cache đối tượng Integer/Long trong khoảng -128..127. So sánh `!=` trên Long
     * vô tình ĐÚNG với id nhỏ, rồi SAI khi id vượt 127 — bug chỉ lộ ra khi hệ thống
     * có trên 127 người dùng, tức là lúc đã lên production.
     *
     * Nếu ai đó sửa Objects.equals thành == hoặc !=, test này sẽ đỏ.
     */
    @Test
    @DisplayName("Chủ sở hữu với userId > 127 — chống regression bug so sánh Long bằng !=")
    void chuSoHuuVoiIdLonHon127() {
        currentUserIs(128L);
        when(postRepository.findOwnerId(1L)).thenReturn(Optional.of(128L));

        assertThat(ownershipService.isOwner(1L, "POST")).isTrue();
    }

    @Test
    @DisplayName("Chủ sở hữu với userId nhỏ (trong Integer cache) vẫn đúng")
    void chuSoHuuVoiIdNho() {
        currentUserIs(5L);
        when(postRepository.findOwnerId(1L)).thenReturn(Optional.of(5L));

        assertThat(ownershipService.isOwner(1L, "POST")).isTrue();
    }

    @Test
    @DisplayName("Người khác không phải chủ sở hữu")
    void nguoiKhacThiKhongPhaiChuSoHuu() {
        currentUserIs(128L);
        when(postRepository.findOwnerId(1L)).thenReturn(Optional.of(999L));

        assertThat(ownershipService.isOwner(1L, "POST")).isFalse();
    }

    @Test
    @DisplayName("Tài nguyên không tồn tại -> từ chối (không lộ id nào có thật)")
    void taiNguyenKhongTonTaiThiTuChoi() {
        currentUserIs(1L);
        when(postRepository.findOwnerId(404L)).thenReturn(Optional.empty());

        assertThat(ownershipService.isOwner(404L, "POST")).isFalse();
    }

    @Test
    @DisplayName("id null -> false, KHÔNG query DB")
    void idNullThiTuChoiVaKhongQueryDb() {
        assertThat(ownershipService.isOwner(null, "POST")).isFalse();
        verify(postRepository, never()).findOwnerId(any());
    }

    @Test
    @DisplayName("Loại tài nguyên chưa khai báo -> ném lỗi để phát hiện sớm khi dev gõ sai")
    void loaiTaiNguyenLaThiNemLoi() {
        assertThatThrownBy(() -> ownershipService.isOwner(1L, "KHONG_TON_TAI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KHONG_TON_TAI");
    }

    @Test
    @DisplayName("Cả 7 loại tài nguyên đều được đăng ký resolver")
    void tatCaLoaiTaiNguyenDeuCoResolver() {
        currentUserIs(1L);
        when(postRepository.findOwnerId(any())).thenReturn(Optional.of(1L));
        when(reviewRepository.findOwnerId(any())).thenReturn(Optional.of(1L));
        when(routeRepository.findOwnerId(any())).thenReturn(Optional.of(1L));
        when(hotspotRepository.findOwnerId(any())).thenReturn(Optional.of(1L));
        when(storyRepository.findOwnerId(any())).thenReturn(Optional.of(1L));
        when(userPlanRepository.findOwnerId(any())).thenReturn(Optional.of(1L));
        when(savedRouteRepository.findOwnerId(any())).thenReturn(Optional.of(1L));

        for (String type : new String[]{"POST", "REVIEW", "ROUTE", "HOTSPOT",
                "STORY", "PLAN", "SAVED_ROUTE"}) {
            assertThat(ownershipService.isOwner(1L, type))
                    .as("Loại " + type + " phải có resolver")
                    .isTrue();
        }
    }

    // ===== isGroupLeader =====

    @Test
    @DisplayName("Trưởng nhóm -> true")
    void truongNhomThiTrue() {
        currentUserIs(7L);
        when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                eq(10L), eq(7L), eq(GroupRole.LEADER))).thenReturn(true);

        assertThat(ownershipService.isGroupLeader(10L)).isTrue();
    }

    @Test
    @DisplayName("Thành viên thường -> false")
    void thanhVienThuongThiFalse() {
        currentUserIs(7L);
        when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                eq(10L), eq(7L), eq(GroupRole.LEADER))).thenReturn(false);

        assertThat(ownershipService.isGroupLeader(10L)).isFalse();
    }

    /**
     * Repository trả Boolean (wrapper), không phải boolean nguyên thuỷ.
     * Nếu code dùng unboxing trực tiếp thay vì Boolean.TRUE.equals(...) thì null -> NPE.
     */
    @Test
    @DisplayName("Repository trả null -> false, KHÔNG ném NullPointerException")
    void repositoryTraNullThiFalse() {
        currentUserIs(7L);
        when(groupParticipantRepository.existsByGroup_GroupIdAndUser_UserIdAndRole(
                any(), any(), any())).thenReturn(null);

        assertThat(ownershipService.isGroupLeader(10L)).isFalse();
    }
}
