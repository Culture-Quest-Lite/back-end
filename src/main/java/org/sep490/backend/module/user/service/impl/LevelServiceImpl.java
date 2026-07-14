package org.sep490.backend.module.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.sep490.backend.common.exception.BusinessException;
import org.sep490.backend.module.authentication.repository.UserRepository;
import org.sep490.backend.module.user.dto.request.LevelRequest;
import org.sep490.backend.module.user.dto.response.LevelResponse;
import org.sep490.backend.module.user.entity.Level;
import org.sep490.backend.module.user.entity.enumeration.LevelStatus;
import org.sep490.backend.module.user.dto.response.LevelProgressResponse;
import org.sep490.backend.module.user.mapper.LevelProgressMapper;
import org.sep490.backend.module.user.mapper.LevelMapper;
import org.sep490.backend.module.user.repository.LevelProgressRepository;
import org.sep490.backend.module.user.repository.LevelRepository;
import org.sep490.backend.module.user.service.LevelService;
import org.sep490.backend.module.user.service.UserService;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.user.entity.LevelProgress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LevelServiceImpl implements LevelService {

    private final LevelRepository levelRepository;
    private final UserRepository userRepository;
    private final LevelMapper levelMapper;
    private final LevelProgressRepository levelProgressRepository;
    private final LevelProgressMapper levelProgressMapper;
    private final UserService userService;

    @Override
    @Transactional
    public LevelResponse createLevel(LevelRequest request) {
        String name = request.getName().trim();
        if (levelRepository.existsByNameAndStatusNot(name, LevelStatus.DELETED)) {
            throw new BusinessException("Tên cấp bậc đã tồn tại");
        }
        if (levelRepository.existsByRequiredXpAndStatusNot(request.getRequiredXp(), LevelStatus.DELETED)) {
            throw new BusinessException("Mức XP yêu cầu đã được sử dụng ở cấp bậc khác");
        }

        Level level = levelMapper.toEntity(request);
        level.setName(name);
        level.setStatus(LevelStatus.ACTIVE);
        level = levelRepository.save(level);
        return levelMapper.toResponse(level);
    }

    @Override
    @Transactional
    public LevelResponse updateLevel(Long levelId, LevelRequest request) {
        Level level = levelRepository.findByLevelIdAndStatusNot(levelId, LevelStatus.DELETED)
                .orElseThrow(() -> new BusinessException("Không tìm thấy cấp bậc"));

        String name = request.getName().trim();
        if (levelRepository.existsByNameAndLevelIdNotAndStatusNot(name, levelId, LevelStatus.DELETED)) {
            throw new BusinessException("Tên cấp bậc đã tồn tại");
        }
        if (levelRepository.existsByRequiredXpAndLevelIdNotAndStatusNot(request.getRequiredXp(), levelId, LevelStatus.DELETED)) {
            throw new BusinessException("Mức XP yêu cầu đã được sử dụng ở cấp bậc khác");
        }

        levelMapper.updateEntityFromRequest(request, level);
        level.setName(name);
        level = levelRepository.save(level);
        return levelMapper.toResponse(level);
    }

    @Override
    @Transactional
    public void deleteLevel(Long levelId) {
        Level level = levelRepository.findByLevelIdAndStatusNot(levelId, LevelStatus.DELETED)
                .orElseThrow(() -> new BusinessException("Không tìm thấy cấp bậc"));

        if (userRepository.existsByLevel_LevelId(levelId)) {
            throw new BusinessException("Không thể xóa cấp bậc này vì đang có người dùng thuộc cấp bậc này");
        }

        level.setStatus(LevelStatus.DELETED);
        levelRepository.save(level);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LevelResponse> getAllLevels() {
        return levelRepository.findAllByStatusNot(LevelStatus.DELETED).stream()
                .map(levelMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LevelResponse getLevelById(Long levelId) {
        Level level = levelRepository.findByLevelIdAndStatusNot(levelId, LevelStatus.DELETED)
                .orElseThrow(() -> new BusinessException("Không tìm thấy cấp bậc"));
        return levelMapper.toResponse(level);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LevelProgressResponse> getMyLevelProgress() {
        User currentUser = userService.getCurrentUser();
        return getLevelProgressByUserId(currentUser.getUserId());
    }

    @Override
    @Transactional
    public List<LevelProgressResponse> getLevelProgressByUserId(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng"));

        List<LevelProgress> progressList = levelProgressRepository.findByUserUserIdOrderByUnlockedAtAsc(userId);
        if (progressList.isEmpty() && user.getLevel() != null) {
            List<Level> levelsToBackfill = levelRepository.findAllByStatusNot(LevelStatus.DELETED).stream()
                    .filter(lvl -> lvl.getRequiredXp() <= user.getLevel().getRequiredXp())
                    .sorted(Comparator.comparingInt(Level::getRequiredXp))
                    .collect(Collectors.toList());

            List<LevelProgress> newProgressList = new ArrayList<>();
            for (Level lvl : levelsToBackfill) {
                LevelProgress lp = LevelProgress.builder()
                        .user(user)
                        .level(lvl)
                        .xpAtUnlock(lvl.getRequiredXp())
                        .unlockedAt(user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now())
                        .build();
                newProgressList.add(levelProgressRepository.save(lp));
            }
            progressList = newProgressList;
        }

        return progressList.stream()
                .map(levelProgressMapper::toResponse)
                .collect(Collectors.toList());
    }
}
