package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.exploration.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {
    boolean existsByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);
}
