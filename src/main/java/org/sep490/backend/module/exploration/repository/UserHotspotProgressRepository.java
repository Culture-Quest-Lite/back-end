package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.exploration.entity.UserHotspotProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserHotspotProgressRepository extends JpaRepository<UserHotspotProgress, Long> {

    Optional<UserHotspotProgress> findByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);

    boolean existsByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);
}
