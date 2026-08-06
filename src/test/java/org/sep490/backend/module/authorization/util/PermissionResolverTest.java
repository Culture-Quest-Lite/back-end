package org.sep490.backend.module.authorization.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sep490.backend.module.authorization.service.PermissionCacheService.UserPermissionView;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logic thuần, không load Spring context — chạy trong mili giây.
 *
 * Đây là nơi kiểm chứng CÔNG THỨC QUYỀN:
 *     quyền = quyền(role) + override granted − override revoked
 */
@DisplayName("PermissionResolver — công thức tính quyền cuối cùng")
class PermissionResolverTest {

    // ===== parseRole: lọc role rác của Keycloak =====

    @Test
    @DisplayName("Bỏ qua role rác Keycloak tự sinh")
    void boQuaRoleRacCuaKeycloak() {
        // realm_access.roles luôn kèm mấy role này, chúng KHÔNG phải role nghiệp vụ
        assertThat(PermissionResolver.parseRole("offline_access")).isNull();
        assertThat(PermissionResolver.parseRole("uma_authorization")).isNull();
        assertThat(PermissionResolver.parseRole("default-roles-culture quest lite")).isNull();
    }

    @Test
    @DisplayName("null không làm vỡ hàm")
    void nullTraVeNull() {
        assertThat(PermissionResolver.parseRole(null)).isNull();
    }

    @Test
    @DisplayName("Nhận diện đúng 4 role nghiệp vụ, không phân biệt hoa thường")
    void nhanDienRoleNghiepVu() {
        assertThat(PermissionResolver.parseRole("ADMIN")).isEqualTo(UserRole.ADMIN);
        assertThat(PermissionResolver.parseRole("curator")).isEqualTo(UserRole.CURATOR);
        assertThat(PermissionResolver.parseRole("Explorer")).isEqualTo(UserRole.EXPLORER);
        assertThat(PermissionResolver.parseRole("PARTNER")).isEqualTo(UserRole.PARTNER);
    }

    // ===== merge: công thức quyền =====

    @Test
    @DisplayName("Không có ngoại lệ -> giữ nguyên quyền của role")
    void khongCoNgoaiLeThiGiuNguyen() {
        Set<String> result = PermissionResolver.merge(
                Set.of("POST_CREATE", "TAG_MANAGE"), Set.of());

        assertThat(result).containsExactlyInAnyOrder("POST_CREATE", "TAG_MANAGE");
    }

    @Test
    @DisplayName("granted=true -> cấp thêm quyền role không có")
    void capThemQuyenNgoaiRole() {
        Set<String> result = PermissionResolver.merge(
                Set.of("POST_CREATE"),
                Set.of(new UserPermissionView("REVIEW_MODERATE", true)));

        assertThat(result).containsExactlyInAnyOrder("POST_CREATE", "REVIEW_MODERATE");
    }

    @Test
    @DisplayName("granted=false -> thu hồi quyền role đang có")
    void thuHoiQuyenCuaRole() {
        Set<String> result = PermissionResolver.merge(
                Set.of("POST_CREATE", "TAG_MANAGE"),
                Set.of(new UserPermissionView("TAG_MANAGE", false)));

        assertThat(result).containsExactly("POST_CREATE");
    }

    /**
     * BẤT BIẾN QUAN TRỌNG NHẤT của toàn bộ hệ thống phân quyền.
     * Nếu test này đỏ, nghĩa là có thể lách quyền bằng cách cấp trùng.
     */
    @Test
    @DisplayName("Vừa cấp vừa thu hồi cùng một quyền -> THU HỒI THẮNG")
    void thuHoiThangCapPhat() {
        Set<String> result = PermissionResolver.merge(
                Set.of(),
                Set.of(new UserPermissionView("TAG_MANAGE", true),
                        new UserPermissionView("TAG_MANAGE", false)));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Thu hồi quyền role KHÔNG có -> không lỗi, không đổi gì")
    void thuHoiQuyenKhongTonTai() {
        Set<String> result = PermissionResolver.merge(
                Set.of("POST_CREATE"),
                Set.of(new UserPermissionView("KHONG_TON_TAI", false)));

        assertThat(result).containsExactly("POST_CREATE");
    }

    @Test
    @DisplayName("merge không làm thay đổi tập đầu vào (tránh hỏng cache Redis dùng chung)")
    void khongLamHongTapDauVao() {
        Set<String> rolePerms = new java.util.HashSet<>(Set.of("POST_CREATE", "TAG_MANAGE"));

        PermissionResolver.merge(rolePerms,
                Set.of(new UserPermissionView("TAG_MANAGE", false),
                        new UserPermissionView("NEW_PERM", true)));

        // Tập gốc đến từ cache Redis — sửa nó sẽ làm hỏng quyền của MỌI user cùng role
        assertThat(rolePerms).containsExactlyInAnyOrder("POST_CREATE", "TAG_MANAGE");
    }

    @Test
    @DisplayName("Nhiều ngoại lệ cùng lúc, cấp và thu hồi đan xen")
    void nhieuNgoaiLeCungLuc() {
        Set<String> result = PermissionResolver.merge(
                Set.of("POST_CREATE", "TAG_MANAGE", "STORY_MANAGE"),
                Set.of(new UserPermissionView("TAG_MANAGE", false),
                        new UserPermissionView("REVIEW_MODERATE", true),
                        new UserPermissionView("ROUTE_MANAGE", true),
                        new UserPermissionView("STORY_MANAGE", false)));

        assertThat(result).containsExactlyInAnyOrder(
                "POST_CREATE", "REVIEW_MODERATE", "ROUTE_MANAGE");
    }
}
