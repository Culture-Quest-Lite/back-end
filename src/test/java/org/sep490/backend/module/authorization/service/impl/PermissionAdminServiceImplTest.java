package org.sep490.backend.module.authorization.service.impl;

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
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.authorization.constant.PermissionCode;
import org.sep490.backend.module.authorization.dto.request.GrantUserPermissionRequest;
import org.sep490.backend.module.authorization.dto.response.PermissionGroupResponse;
import org.sep490.backend.module.authorization.dto.response.UserPermissionResponse;
import org.sep490.backend.module.authorization.entity.Permission;
import org.sep490.backend.module.authorization.entity.RolePermission;
import org.sep490.backend.module.authorization.entity.UserPermission;
import org.sep490.backend.module.authorization.repository.PermissionRepository;
import org.sep490.backend.module.authorization.repository.RolePermissionRepository;
import org.sep490.backend.module.authorization.repository.UserPermissionRepository;
import org.sep490.backend.module.authorization.service.PermissionCacheService;
import org.sep490.backend.module.user.entity.enumeration.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho quản trị PHÂN QUYỀN (Authorization).
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionAdminServiceImplTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserPermissionRepository userPermissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private PermissionCacheService permissionCacheService;

    @InjectMocks private PermissionAdminServiceImpl permissionAdminService;

    private static Permission permission(Long id, String code, String groupName) {
        return Permission.builder()
                .permissionId(id)
                .code(code)
                .groupName(groupName)
                .description("Mô tả " + code)
                .active(true)
                .build();
    }

    // =====================================================================
    // Function: replacePermissions
    // =====================================================================
    @Nested
    @DisplayName("replacePermissions")
    class ReplacePermissionsTest {

        // UTCID01 - Abnormal: danh sách chứa mã quyền không tồn tại
        @Test
        void replacePermissions_unknownCode_throwsCodeNotFound() {
            when(permissionRepository.existsByCode("POST_MODERATE")).thenReturn(true);
            when(permissionRepository.existsByCode("KHONG_TON_TAI")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.replacePermissions(
                            UserRole.CURATOR, List.of("POST_MODERATE", "KHONG_TON_TAI")));

            assertEquals("Mã quyền không tồn tại: KHONG_TON_TAI", ex.getMessage());
            verify(rolePermissionRepository, never()).deleteByRole(any());
            verify(permissionCacheService, never()).evictAllRoles();
        }

        // UTCID02 - Abnormal: nhiều mã sai cùng lúc -> gộp vào một message
        @Test
        void replacePermissions_multipleUnknownCodes_listsAllInMessage() {
            when(permissionRepository.existsByCode(anyString())).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.replacePermissions(
                            UserRole.CURATOR, List.of("SAI_1", "SAI_2")));

            assertEquals("Mã quyền không tồn tại: SAI_1, SAI_2", ex.getMessage());
            verify(rolePermissionRepository, never()).deleteByRole(any());
        }

        // UTCID03 - Abnormal: gỡ PERMISSION_MANAGE khỏi ADMIN -> tự khóa hệ thống
        @Test
        void replacePermissions_adminWithoutPermissionManage_throwsLockout() {
            when(permissionRepository.existsByCode("USER_LOCK")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.replacePermissions(
                            UserRole.ADMIN, List.of("USER_LOCK")));

            assertEquals("Không thể gỡ quyền cấu hình phân quyền khỏi vai trò ADMIN", ex.getMessage());
            verify(rolePermissionRepository, never()).deleteByRole(any());
            verify(permissionCacheService, never()).evictAllRoles();
        }

        // UTCID04 - Normal: ADMIN vẫn giữ PERMISSION_MANAGE -> cho phép thay thế
        @Test
        void replacePermissions_adminKeepsPermissionManage_replacesAndEvicts() {
            when(permissionRepository.existsByCode(anyString())).thenReturn(true);
            when(permissionRepository.findByCode("PERMISSION_MANAGE"))
                    .thenReturn(Optional.of(permission(1L, "PERMISSION_MANAGE", "SYSTEM")));
            when(permissionRepository.findByCode("USER_LOCK"))
                    .thenReturn(Optional.of(permission(2L, "USER_LOCK", "USER")));

            permissionAdminService.replacePermissions(
                    UserRole.ADMIN, List.of("PERMISSION_MANAGE", "USER_LOCK"));

            verify(rolePermissionRepository).deleteByRole(UserRole.ADMIN);
            verify(rolePermissionRepository).flush();
            verify(rolePermissionRepository, times(2)).save(any(RolePermission.class));
            verify(permissionCacheService).evictAllRoles();
        }

        // UTCID05 - Boundary: danh sách trùng lặp -> chỉ lưu 1 lần (distinct)
        @Test
        void replacePermissions_duplicateCodes_savesOnlyOnce() {
            when(permissionRepository.existsByCode("TAG_MANAGE")).thenReturn(true);
            when(permissionRepository.findByCode("TAG_MANAGE"))
                    .thenReturn(Optional.of(permission(3L, "TAG_MANAGE", "CONTENT")));

            permissionAdminService.replacePermissions(
                    UserRole.CURATOR, List.of("TAG_MANAGE", "TAG_MANAGE", "TAG_MANAGE"));

            verify(rolePermissionRepository, times(1)).save(any(RolePermission.class));
            verify(permissionCacheService).evictAllRoles();
        }

        // UTCID06 - Boundary: danh sách rỗng cho role thường -> xóa sạch quyền
        @Test
        void replacePermissions_emptyListForNonAdmin_clearsAllPermissions() {
            permissionAdminService.replacePermissions(UserRole.EXPLORER, List.of());

            verify(rolePermissionRepository).deleteByRole(UserRole.EXPLORER);
            verify(rolePermissionRepository, never()).save(any(RolePermission.class));
            verify(permissionCacheService).evictAllRoles();
        }
    }

    // =====================================================================
    // Function: grant
    // =====================================================================
    @Nested
    @DisplayName("grant")
    class GrantTest {

        // UTCID01 - Normal: role đã có sẵn quyền -> idempotent, không ghi DB
        @Test
        void grant_alreadyHasPermission_returnsWithoutSaving() {
            when(rolePermissionRepository.existsByRoleAndPermission_Code(UserRole.CURATOR, "TAG_MANAGE"))
                    .thenReturn(true);

            permissionAdminService.grant(UserRole.CURATOR, "TAG_MANAGE");

            verify(rolePermissionRepository, never()).save(any());
            verify(permissionCacheService, never()).evictAllRoles();
        }

        // UTCID02 - Abnormal: mã quyền không tồn tại
        @Test
        void grant_unknownCode_throwsCodeNotFound() {
            when(rolePermissionRepository.existsByRoleAndPermission_Code(any(), anyString()))
                    .thenReturn(false);
            when(permissionRepository.findByCode("KHONG_TON_TAI")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.grant(UserRole.CURATOR, "KHONG_TON_TAI"));

            assertEquals("Mã quyền không tồn tại: KHONG_TON_TAI", ex.getMessage());
            verify(rolePermissionRepository, never()).save(any());
            verify(permissionCacheService, never()).evictAllRoles();
        }

        // UTCID03 - Normal: cấp quyền mới thành công -> lưu và xóa cache
        @Test
        void grant_newPermission_savesAndEvictsCache() {
            Permission tagManage = permission(3L, "TAG_MANAGE", "CONTENT");
            when(rolePermissionRepository.existsByRoleAndPermission_Code(UserRole.CURATOR, "TAG_MANAGE"))
                    .thenReturn(false);
            when(permissionRepository.findByCode("TAG_MANAGE")).thenReturn(Optional.of(tagManage));

            permissionAdminService.grant(UserRole.CURATOR, "TAG_MANAGE");

            ArgumentCaptor<RolePermission> captor = ArgumentCaptor.forClass(RolePermission.class);
            verify(rolePermissionRepository).save(captor.capture());
            assertEquals(UserRole.CURATOR, captor.getValue().getRole());
            assertEquals("TAG_MANAGE", captor.getValue().getPermission().getCode());
            verify(permissionCacheService).evictAllRoles();
        }
    }

    // =====================================================================
    // Function: revoke
    // =====================================================================
    @Nested
    @DisplayName("revoke")
    class RevokeTest {

        // UTCID01 - Abnormal: gỡ PERMISSION_MANAGE khỏi ADMIN -> bị chặn
        @Test
        void revoke_permissionManageFromAdmin_throwsLockout() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.revoke(UserRole.ADMIN, PermissionCode.PERMISSION_MANAGE));

            assertEquals("Không thể gỡ quyền cấu hình phân quyền khỏi vai trò ADMIN", ex.getMessage());
            verify(rolePermissionRepository, never()).deleteByRoleAndPermission_Code(any(), anyString());
            verify(permissionCacheService, never()).evictAllRoles();
        }

        // UTCID02 - Normal: gỡ PERMISSION_MANAGE khỏi role KHÁC ADMIN -> được phép
        @Test
        void revoke_permissionManageFromNonAdmin_deletesSuccessfully() {
            permissionAdminService.revoke(UserRole.CURATOR, PermissionCode.PERMISSION_MANAGE);

            verify(rolePermissionRepository)
                    .deleteByRoleAndPermission_Code(UserRole.CURATOR, "PERMISSION_MANAGE");
            verify(permissionCacheService).evictAllRoles();
        }

        // UTCID03 - Normal: gỡ quyền thường khỏi ADMIN -> được phép
        @Test
        void revoke_otherPermissionFromAdmin_deletesSuccessfully() {
            permissionAdminService.revoke(UserRole.ADMIN, PermissionCode.USER_LOCK);

            verify(rolePermissionRepository)
                    .deleteByRoleAndPermission_Code(UserRole.ADMIN, "USER_LOCK");
            verify(permissionCacheService).evictAllRoles();
        }
    }

    // =====================================================================
    // Function: upsertUserPermission
    // =====================================================================
    @Nested
    @DisplayName("upsertUserPermission")
    class UpsertUserPermissionTest {

        private static GrantUserPermissionRequest request(String code, boolean granted) {
            GrantUserPermissionRequest request = new GrantUserPermissionRequest();
            request.setCode(code);
            request.setGranted(granted);
            request.setReason("Hỗ trợ đợt kiểm duyệt tháng 8");
            request.setExpiresAt(LocalDateTime.of(2026, 9, 1, 0, 0));
            return request;
        }

        private static User user(Long id, UserRole role) {
            User user = new User();
            user.setUserId(id);
            user.setRole(role);
            return user;
        }

        // UTCID01 - Abnormal: không tìm thấy người dùng
        @Test
        void upsertUserPermission_userNotFound_throwsUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.upsertUserPermission(99L, request("REVIEW_MODERATE", true)));

            assertEquals("Không tìm thấy người dùng", ex.getMessage());
            verify(userPermissionRepository, never()).save(any());
            verify(permissionCacheService, never()).evictUser(anyLong());
        }

        // UTCID02 - Abnormal: mã quyền không tồn tại
        @Test
        void upsertUserPermission_unknownCode_throwsCodeNotFound() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserRole.CURATOR)));
            when(permissionRepository.findByCode("KHONG_TON_TAI")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.upsertUserPermission(1L, request("KHONG_TON_TAI", true)));

            assertEquals("Mã quyền không tồn tại: KHONG_TON_TAI", ex.getMessage());
            verify(userPermissionRepository, never()).save(any());
        }

        // UTCID03 - Abnormal: thu hồi PERMISSION_MANAGE của một tài khoản ADMIN -> tự khóa
        @Test
        void upsertUserPermission_revokePermissionManageFromAdminUser_throwsSelfLockout() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, UserRole.ADMIN)));
            when(permissionRepository.findByCode("PERMISSION_MANAGE"))
                    .thenReturn(Optional.of(permission(1L, "PERMISSION_MANAGE", "SYSTEM")));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.upsertUserPermission(1L, request("PERMISSION_MANAGE", false)));

            assertEquals("Không thể thu hồi quyền cấu hình phân quyền của một tài khoản ADMIN",
                    ex.getMessage());
            verify(userPermissionRepository, never()).save(any());
            verify(permissionCacheService, never()).evictUser(anyLong());
        }

        // UTCID04 - Normal: chưa có bản ghi -> tạo ngoại lệ quyền mới
        @Test
        void upsertUserPermission_noExistingRecord_createsNewOverride() {
            User target = user(1L, UserRole.CURATOR);
            Permission reviewModerate = permission(5L, "REVIEW_MODERATE", "REVIEW");
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(permissionRepository.findByCode("REVIEW_MODERATE")).thenReturn(Optional.of(reviewModerate));
            when(userPermissionRepository.findByUser_UserIdAndPermission_Code(1L, "REVIEW_MODERATE"))
                    .thenReturn(Optional.empty());

            permissionAdminService.upsertUserPermission(1L, request("REVIEW_MODERATE", true));

            ArgumentCaptor<UserPermission> captor = ArgumentCaptor.forClass(UserPermission.class);
            verify(userPermissionRepository).save(captor.capture());
            UserPermission saved = captor.getValue();
            assertSame(target, saved.getUser());
            assertSame(reviewModerate, saved.getPermission());
            assertTrue(saved.isGranted());
            assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), saved.getExpiresAt());
            assertEquals("Hỗ trợ đợt kiểm duyệt tháng 8", saved.getReason());
            verify(permissionCacheService).evictUser(1L);
        }

        // UTCID05 - Normal: đã có bản ghi -> cập nhật đè lên bản ghi cũ
        @Test
        void upsertUserPermission_existingRecord_updatesInPlace() {
            User target = user(1L, UserRole.CURATOR);
            UserPermission existing = UserPermission.builder()
                    .id(77L)
                    .user(target)
                    .permission(permission(5L, "REVIEW_MODERATE", "REVIEW"))
                    .granted(true)
                    .reason("Lý do cũ")
                    .build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(target));
            when(permissionRepository.findByCode("REVIEW_MODERATE"))
                    .thenReturn(Optional.of(permission(5L, "REVIEW_MODERATE", "REVIEW")));
            when(userPermissionRepository.findByUser_UserIdAndPermission_Code(1L, "REVIEW_MODERATE"))
                    .thenReturn(Optional.of(existing));

            permissionAdminService.upsertUserPermission(1L, request("REVIEW_MODERATE", false));

            verify(userPermissionRepository).save(existing);
            assertEquals(77L, existing.getId());
            assertFalse(existing.isGranted());
            assertEquals("Hỗ trợ đợt kiểm duyệt tháng 8", existing.getReason());
            verify(permissionCacheService).evictUser(1L);
        }

        // UTCID06 - Boundary: thu hồi PERMISSION_MANAGE của user KHÔNG phải ADMIN -> được phép
        @Test
        void upsertUserPermission_revokePermissionManageFromNonAdmin_allowed() {
            User target = user(2L, UserRole.CURATOR);
            when(userRepository.findById(2L)).thenReturn(Optional.of(target));
            when(permissionRepository.findByCode("PERMISSION_MANAGE"))
                    .thenReturn(Optional.of(permission(1L, "PERMISSION_MANAGE", "SYSTEM")));
            when(userPermissionRepository.findByUser_UserIdAndPermission_Code(2L, "PERMISSION_MANAGE"))
                    .thenReturn(Optional.empty());

            permissionAdminService.upsertUserPermission(2L, request("PERMISSION_MANAGE", false));

            verify(userPermissionRepository).save(any(UserPermission.class));
            verify(permissionCacheService).evictUser(2L);
        }
    }

    // =====================================================================
    // Function: getUserPermissions
    // =====================================================================
    @Nested
    @DisplayName("getUserPermissions")
    class GetUserPermissionsTest {

        private static UserPermission userPermission(Long id, String code, boolean granted,
                                                     LocalDateTime expiresAt) {
            return UserPermission.builder()
                    .id(id)
                    .permission(permission(id, code, "REVIEW"))
                    .granted(granted)
                    .expiresAt(expiresAt)
                    .reason("Lý do " + code)
                    .build();
        }

        // UTCID01 - Normal: quyền vĩnh viễn (expiresAt = null) -> expired = false
        @Test
        void getUserPermissions_neverExpires_expiredIsFalse() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(userPermissionRepository.findAllByUserIdWithPermission(1L))
                    .thenReturn(List.of(userPermission(1L, "REVIEW_MODERATE", true, null)));

            List<UserPermissionResponse> result = permissionAdminService.getUserPermissions(1L);

            assertEquals(1, result.size());
            assertFalse(result.get(0).isExpired());
            assertTrue(result.get(0).isGranted());
            assertEquals("REVIEW_MODERATE", result.get(0).getCode());
        }

        // UTCID02 - Abnormal: quyền đã quá hạn -> expired = true
        @Test
        void getUserPermissions_pastExpiry_expiredIsTrue() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(userPermissionRepository.findAllByUserIdWithPermission(1L))
                    .thenReturn(List.of(userPermission(1L, "REVIEW_MODERATE", true,
                            LocalDateTime.now().minusDays(1))));

            List<UserPermissionResponse> result = permissionAdminService.getUserPermissions(1L);

            assertTrue(result.get(0).isExpired());
        }

        // UTCID03 - Boundary: quyền còn hạn 1 giờ -> expired = false
        @Test
        void getUserPermissions_futureExpiry_expiredIsFalse() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(userPermissionRepository.findAllByUserIdWithPermission(1L))
                    .thenReturn(List.of(userPermission(1L, "REVIEW_MODERATE", true,
                            LocalDateTime.now().plusHours(1))));

            List<UserPermissionResponse> result = permissionAdminService.getUserPermissions(1L);

            assertFalse(result.get(0).isExpired());
        }

        // UTCID04 - Boundary: user không có ngoại lệ quyền nào -> danh sách rỗng
        @Test
        void getUserPermissions_noOverrides_returnsEmptyList() {
            when(userRepository.existsById(1L)).thenReturn(true);
            when(userPermissionRepository.findAllByUserIdWithPermission(1L)).thenReturn(List.of());

            assertTrue(permissionAdminService.getUserPermissions(1L).isEmpty());
        }

        // UTCID05 - Abnormal: userId = null -> không xác định được người dùng
        @Test
        void getUserPermissions_nullUserId_throwsUserNotIdentified() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.getUserPermissions(null));

            assertEquals("Không xác định được người dùng", ex.getMessage());
            verifyNoInteractions(userPermissionRepository);
        }

        // UTCID06 - Abnormal: userId = 999 không tồn tại -> không tìm thấy người dùng
        @Test
        void getUserPermissions_userNotFound_throwsUserNotFound() {
            when(userRepository.existsById(999L)).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> permissionAdminService.getUserPermissions(999L));

            assertEquals("Không tìm thấy người dùng", ex.getMessage());
            verifyNoInteractions(userPermissionRepository);
        }
    }

    // =====================================================================
    // Function: listGrouped
    // =====================================================================
    @Nested
    @DisplayName("listGrouped")
    class ListGroupedTest {

        // UTCID01 - Normal: gom nhóm theo groupName
        @Test
        void listGrouped_multipleGroups_groupsByGroupName() {
            when(permissionRepository.findAllByActiveTrueOrderByGroupNameAscCodeAsc())
                    .thenReturn(List.of(
                            permission(1L, "USER_LOCK", "USER"),
                            permission(2L, "USER_VIEW_ALL", "USER"),
                            permission(3L, "TAG_MANAGE", "CONTENT")));

            List<PermissionGroupResponse> result = permissionAdminService.listGrouped();

            assertEquals(2, result.size());
            assertEquals("USER", result.get(0).getGroupName());
            assertEquals(2, result.get(0).getPermissions().size());
            assertEquals("CONTENT", result.get(1).getGroupName());
        }

        // UTCID02 - Abnormal: groupName = null -> gom vào nhóm "KHÁC"
        @Test
        void listGrouped_nullGroupName_fallsBackToKhac() {
            when(permissionRepository.findAllByActiveTrueOrderByGroupNameAscCodeAsc())
                    .thenReturn(List.of(permission(1L, "LEGACY_CODE", null)));

            List<PermissionGroupResponse> result = permissionAdminService.listGrouped();

            assertEquals(1, result.size());
            assertEquals("KHÁC", result.get(0).getGroupName());
        }

        // UTCID03 - Boundary: không có quyền nào đang active -> danh sách rỗng
        @Test
        void listGrouped_noActivePermissions_returnsEmptyList() {
            when(permissionRepository.findAllByActiveTrueOrderByGroupNameAscCodeAsc())
                    .thenReturn(List.of());

            assertTrue(permissionAdminService.listGrouped().isEmpty());
        }
    }

    // =====================================================================
    // Function: deleteUserPermission
    // =====================================================================
    @Nested
    @DisplayName("deleteUserPermission")
    class DeleteUserPermissionTest {

        // UTCID01 - Normal: xóa ngoại lệ quyền -> gọi delete và xóa cache của user
        @Test
        void deleteUserPermission_valid_deletesAndEvictsUserCache() {
            permissionAdminService.deleteUserPermission(1L, "REVIEW_MODERATE");

            verify(userPermissionRepository)
                    .deleteByUser_UserIdAndPermission_Code(1L, "REVIEW_MODERATE");
            verify(permissionCacheService).evictUser(1L);
        }

        // UTCID02 - Boundary: xóa mã không tồn tại -> vẫn evict, không ném lỗi
        @Test
        void deleteUserPermission_nonExistingCode_stillEvictsWithoutError() {
            assertDoesNotThrow(() -> permissionAdminService.deleteUserPermission(1L, "KHONG_TON_TAI"));

            verify(permissionCacheService).evictUser(1L);
        }
    }
}
