package org.sep490.backend.module.user.service;

import org.sep490.backend.module.user.dto.request.LevelRequest;
import org.sep490.backend.module.user.dto.response.LevelProgressResponse;
import org.sep490.backend.module.user.dto.response.LevelResponse;

import java.util.List;

public interface LevelService {
    LevelResponse createLevel(LevelRequest request);
    LevelResponse updateLevel(Long levelId, LevelRequest request);
    void deleteLevel(Long levelId);
    List<LevelResponse> getAllLevels();
    LevelResponse getLevelById(Long levelId);
    List<LevelProgressResponse> getMyLevelProgress();
    List<LevelProgressResponse> getLevelProgressByUserId(Long userId);
}
