package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.exploration.entity.UserHotspotProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserHotspotProgressRepository extends JpaRepository<UserHotspotProgress, Long> {

    Optional<UserHotspotProgress> findByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);

    boolean existsByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);

    @Query("SELECT p.user.userId, COUNT(p.hotspot.hotspotId) " +
            "FROM UserHotspotProgress p " +
            "WHERE p.user IN :users AND p.hotspot IN :hotspots " +
            "GROUP BY p.user.userId")
    List<Object[]> countCompletedHotspotsRaw(
            @Param("users") List<User> users,
            @Param("hotspots") List<Hotspot> hotspots
    );
}
