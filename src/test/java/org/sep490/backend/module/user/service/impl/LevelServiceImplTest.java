package org.sep490.backend.module.user.service.impl;

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
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.request.LevelRequest;
import org.sep490.backend.module.user.dto.response.LevelResponse;
import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.LevelProgress;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.user.mapper.LevelMapper;
import org.sep490.backend.module.user.mapper.LevelProgressMapper;
import org.sep490.backend.module.user.repository.LevelProgressRepository;
import org.sep490.backend.module.user.repository.LevelRepository;
import org.sep490.backend.module.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho CẤP BẬC (Level) và tiến trình lên cấp.
 * Mỗi test method tương ứng 1 cột UTCID trong sheet unit test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LevelServiceImplTest {

    @Mock private LevelRepository levelRepository;
    @Mock private UserRepository userRepository;
    @Mock private LevelMapper levelMapper;
    @Mock private LevelProgressRepository levelProgressRepository;
    @Mock private LevelProgressMapper levelProgressMapper;
    @Mock private UserService userService;

    @InjectMocks private LevelServiceImpl levelService;

    private static Level level(Long id, String name, int requiredXp) {
        return Level.builder()
                .levelId(id)
                .name(name)
                .requiredXp(requiredXp)
                .description("Cấp bậc " + name)
                .status(LevelStatus.ACTIVE)
                .build();
    }

    private static LevelRequest levelRequest(String name, Integer requiredXp) {
        LevelRequest request = new LevelRequest();
        request.setName(name);
        request.setRequiredXp(requiredXp);
        request.setDescription("Nhà thám hiểm mới vào nghề");
        return request;
    }

    // =====================================================================
    // Function: createLevel
    // =====================================================================
    @Nested
    @DisplayName("createLevel")
    class CreateLevelTest {

        // UTCID01 - Abnormal: tên cấp bậc đã tồn tại
        @Test
        void createLevel_duplicateName_throwsNameExists() {
            when(levelRepository.existsByNameAndStatusNot("Tân Binh", LevelStatus.DELETED))
                    .thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.createLevel(levelRequest("Tân Binh", 0)));

            assertEquals("Tên cấp bậc đã tồn tại", ex.getMessage());
            verify(levelRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: mức XP đã dùng ở cấp bậc khác
        @Test
        void createLevel_duplicateRequiredXp_throwsXpUsed() {
            when(levelRepository.existsByNameAndStatusNot(anyString(), any())).thenReturn(false);
            when(levelRepository.existsByRequiredXpAndStatusNot(1000, LevelStatus.DELETED))
                    .thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.createLevel(levelRequest("Kỳ Cựu", 1000)));

            assertEquals("Mức XP yêu cầu đã được sử dụng ở cấp bậc khác", ex.getMessage());
            verify(levelRepository, never()).save(any());
        }

        // UTCID03 - Normal: tạo thành công -> status ACTIVE
        @Test
        void createLevel_validRequest_createsActiveLevel() {
            Level mapped = level(null, null, 1000);
            when(levelRepository.existsByNameAndStatusNot(anyString(), any())).thenReturn(false);
            when(levelRepository.existsByRequiredXpAndStatusNot(anyInt(), any())).thenReturn(false);
            when(levelMapper.toEntity(any(LevelRequest.class))).thenReturn(mapped);
            when(levelRepository.save(any(Level.class))).thenAnswer(inv -> inv.getArgument(0));

            LevelResponse expected = mock(LevelResponse.class);
            when(levelMapper.toResponse(mapped)).thenReturn(expected);

            assertSame(expected, levelService.createLevel(levelRequest("Kỳ Cựu", 1000)));
            assertEquals("Kỳ Cựu", mapped.getName());
            assertEquals(LevelStatus.ACTIVE, mapped.getStatus());
        }

        // UTCID04 - Boundary: tên có khoảng trắng thừa -> được cắt bỏ (trim)
        @Test
        void createLevel_nameWithExtraSpaces_isTrimmed() {
            Level mapped = level(null, null, 1000);
            when(levelRepository.existsByNameAndStatusNot(anyString(), any())).thenReturn(false);
            when(levelRepository.existsByRequiredXpAndStatusNot(anyInt(), any())).thenReturn(false);
            when(levelMapper.toEntity(any(LevelRequest.class))).thenReturn(mapped);
            when(levelRepository.save(any(Level.class))).thenAnswer(inv -> inv.getArgument(0));

            levelService.createLevel(levelRequest("   Kỳ Cựu   ", 1000));

            assertEquals("Kỳ Cựu", mapped.getName());
            verify(levelRepository).existsByNameAndStatusNot("Kỳ Cựu", LevelStatus.DELETED);
        }

        // UTCID05 - Boundary: XP yêu cầu = 0 (cấp bậc thấp nhất) -> hợp lệ
        @Test
        void createLevel_zeroRequiredXp_isAccepted() {
            Level mapped = level(null, null, 0);
            when(levelRepository.existsByNameAndStatusNot(anyString(), any())).thenReturn(false);
            when(levelRepository.existsByRequiredXpAndStatusNot(0, LevelStatus.DELETED)).thenReturn(false);
            when(levelMapper.toEntity(any(LevelRequest.class))).thenReturn(mapped);
            when(levelRepository.save(any(Level.class))).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> levelService.createLevel(levelRequest("Tân Binh", 0)));
            assertEquals(LevelStatus.ACTIVE, mapped.getStatus());
        }
    }

    // =====================================================================
    // Function: updateLevel
    // =====================================================================
    @Nested
    @DisplayName("updateLevel")
    class UpdateLevelTest {

        // UTCID01 - Abnormal: cấp bậc không tồn tại (hoặc đã bị xóa)
        @Test
        void updateLevel_notFound_throwsLevelNotFound() {
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.updateLevel(1L, levelRequest("Kỳ Cựu", 1000)));

            assertEquals("Không tìm thấy cấp bậc", ex.getMessage());
            verify(levelRepository, never()).save(any());
        }

        // UTCID02 - Abnormal: tên mới trùng với cấp bậc khác
        @Test
        void updateLevel_duplicateNameOnOtherLevel_throwsNameExists() {
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.of(level(1L, "Tân Binh", 0)));
            when(levelRepository.existsByNameAndLevelIdNotAndStatusNot(
                    "Kỳ Cựu", 1L, LevelStatus.DELETED)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.updateLevel(1L, levelRequest("Kỳ Cựu", 1000)));

            assertEquals("Tên cấp bậc đã tồn tại", ex.getMessage());
        }

        // UTCID03 - Abnormal: mức XP mới trùng với cấp bậc khác
        @Test
        void updateLevel_duplicateXpOnOtherLevel_throwsXpUsed() {
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.of(level(1L, "Tân Binh", 0)));
            when(levelRepository.existsByNameAndLevelIdNotAndStatusNot(anyString(), anyLong(), any()))
                    .thenReturn(false);
            when(levelRepository.existsByRequiredXpAndLevelIdNotAndStatusNot(
                    1000, 1L, LevelStatus.DELETED)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.updateLevel(1L, levelRequest("Tân Binh", 1000)));

            assertEquals("Mức XP yêu cầu đã được sử dụng ở cấp bậc khác", ex.getMessage());
        }

        // UTCID04 - Normal: cập nhật hợp lệ -> gọi mapper và lưu, tên được trim
        @Test
        void updateLevel_validRequest_updatesAndTrimsName() {
            Level target = level(1L, "Tân Binh", 0);
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.of(target));
            when(levelRepository.existsByNameAndLevelIdNotAndStatusNot(anyString(), anyLong(), any()))
                    .thenReturn(false);
            when(levelRepository.existsByRequiredXpAndLevelIdNotAndStatusNot(anyInt(), anyLong(), any()))
                    .thenReturn(false);
            when(levelRepository.save(any(Level.class))).thenAnswer(inv -> inv.getArgument(0));

            LevelRequest request = levelRequest("  Kỳ Cựu  ", 1000);
            levelService.updateLevel(1L, request);

            verify(levelMapper).updateEntityFromRequest(request, target);
            assertEquals("Kỳ Cựu", target.getName());
            verify(levelRepository).save(target);
        }
    }

    // =====================================================================
    // Function: deleteLevel
    // =====================================================================
    @Nested
    @DisplayName("deleteLevel")
    class DeleteLevelTest {

        // UTCID01 - Abnormal: cấp bậc không tồn tại
        @Test
        void deleteLevel_notFound_throwsLevelNotFound() {
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.deleteLevel(1L));

            assertEquals("Không tìm thấy cấp bậc", ex.getMessage());
        }

        // UTCID02 - Abnormal: còn người dùng đang ở cấp bậc này -> không cho xóa
        @Test
        void deleteLevel_stillHasUsers_throwsInUse() {
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.of(level(1L, "Tân Binh", 0)));
            when(userRepository.existsByLevel_LevelId(1L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.deleteLevel(1L));

            assertEquals("Không thể xóa cấp bậc này vì đang có người dùng thuộc cấp bậc này",
                    ex.getMessage());
            verify(levelRepository, never()).save(any());
        }

        // UTCID03 - Normal: không còn người dùng -> xóa mềm, status DELETED
        @Test
        void deleteLevel_noUsers_setsStatusDeleted() {
            Level target = level(1L, "Tân Binh", 0);
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.of(target));
            when(userRepository.existsByLevel_LevelId(1L)).thenReturn(false);

            levelService.deleteLevel(1L);

            assertEquals(LevelStatus.DELETED, target.getStatus());
            verify(levelRepository).save(target);
        }
    }

    // =====================================================================
    // Function: getLevelProgressByUserId
    // =====================================================================
    @Nested
    @DisplayName("getLevelProgressByUserId")
    class GetLevelProgressByUserIdTest {

        private static User userWithLevel(Long userId, Level level) {
            User user = new User();
            user.setUserId(userId);
            user.setLevel(level);
            user.setCreatedAt(LocalDateTime.of(2026, 1, 15, 8, 0));
            return user;
        }

        // UTCID01 - Abnormal: người dùng không tồn tại
        @Test
        void getLevelProgressByUserId_userNotFound_throwsUserNotFound() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.getLevelProgressByUserId(99L));

            assertEquals("Không tìm thấy thông tin người dùng", ex.getMessage());
        }

        // UTCID02 - Normal: đã có lịch sử lên cấp -> trả về nguyên, không backfill
        @Test
        void getLevelProgressByUserId_hasProgress_returnsWithoutBackfill() {
            User user = userWithLevel(1L, level(2L, "Kỳ Cựu", 1000));
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(levelProgressRepository.findByUserUserIdOrderByUnlockedAtAsc(1L))
                    .thenReturn(List.of(new LevelProgress(), new LevelProgress()));

            assertEquals(2, levelService.getLevelProgressByUserId(1L).size());
            verify(levelProgressRepository, never()).save(any());
        }

        // UTCID03 - Boundary: user cũ chưa có lịch sử -> backfill mọi cấp bậc <= cấp hiện tại
        @Test
        void getLevelProgressByUserId_noProgress_backfillsLowerLevels() {
            Level current = level(3L, "Kỳ Cựu", 1000);
            User user = userWithLevel(1L, current);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(levelProgressRepository.findByUserUserIdOrderByUnlockedAtAsc(1L))
                    .thenReturn(List.of());
            when(levelRepository.findAllByStatusNot(LevelStatus.DELETED)).thenReturn(List.of(
                    level(1L, "Tân Binh", 0),
                    level(2L, "Khá", 500),
                    current,
                    level(4L, "Huyền Thoại", 5000)));
            when(levelProgressRepository.save(any(LevelProgress.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            levelService.getLevelProgressByUserId(1L);

            // Chỉ backfill 3 cấp có requiredXp <= 1000, bỏ cấp 5000
            verify(levelProgressRepository, times(3)).save(any(LevelProgress.class));
        }

        // UTCID04 - Boundary: user chưa có cấp bậc nào (level = null) -> không backfill
        @Test
        void getLevelProgressByUserId_userWithoutLevel_doesNotBackfill() {
            User user = userWithLevel(1L, null);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(levelProgressRepository.findByUserUserIdOrderByUnlockedAtAsc(1L))
                    .thenReturn(List.of());

            assertTrue(levelService.getLevelProgressByUserId(1L).isEmpty());
            verify(levelProgressRepository, never()).save(any());
        }
    }

    // =====================================================================
    // Function: getLevelById / getAllLevels
    // =====================================================================
    @Nested
    @DisplayName("getLevelById")
    class GetLevelByIdTest {

        // UTCID01 - Abnormal: cấp bậc không tồn tại hoặc đã xóa
        @Test
        void getLevelById_notFound_throwsLevelNotFound() {
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> levelService.getLevelById(1L));

            assertEquals("Không tìm thấy cấp bậc", ex.getMessage());
        }

        // UTCID02 - Normal: tìm thấy -> trả về response đã map
        @Test
        void getLevelById_found_returnsMappedResponse() {
            Level target = level(1L, "Tân Binh", 0);
            when(levelRepository.findByLevelIdAndStatusNot(1L, LevelStatus.DELETED))
                    .thenReturn(Optional.of(target));

            LevelResponse expected = mock(LevelResponse.class);
            when(levelMapper.toResponse(target)).thenReturn(expected);

            assertSame(expected, levelService.getLevelById(1L));
        }

        // UTCID03 - Normal: getAllLevels chỉ lấy cấp bậc chưa bị xóa
        @Test
        void getAllLevels_excludesDeletedLevels() {
            when(levelRepository.findAllByStatusNot(LevelStatus.DELETED))
                    .thenReturn(List.of(level(1L, "Tân Binh", 0), level(2L, "Kỳ Cựu", 1000)));

            assertEquals(2, levelService.getAllLevels().size());
            verify(levelRepository).findAllByStatusNot(LevelStatus.DELETED);
        }
    }
}
