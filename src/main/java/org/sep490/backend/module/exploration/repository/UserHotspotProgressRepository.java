package org.sep490.backend.module.exploration.repository;

import org.sep490.backend.module.admin.dto.projection.DailyCountProjection;
import org.sep490.backend.module.authentication.entity.User;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.curator.dto.projection.HotspotCheckInCountProjection;
import org.sep490.backend.module.exploration.entity.UserHotspotProgress;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserHotspotProgressRepository extends JpaRepository<UserHotspotProgress, Long> {

    Optional<UserHotspotProgress> findByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);

    boolean existsByUser_UserIdAndHotspot_HotspotId(Long userId, Long hotspotId);

    @Query("SELECT p.hotspot.hotspotId FROM UserHotspotProgress p " +
            "WHERE p.user.userId = :userId AND p.hotspot.hotspotId IN :hotspotIds")
    List<Long> findCheckedInHotspotIds(@Param("userId") Long userId,
                                       @Param("hotspotIds") List<Long> hotspotIds);

    @Query("SELECT p.user.userId, COUNT(p.hotspot.hotspotId) " +
            "FROM UserHotspotProgress p " +
            "WHERE p.user IN :users AND p.hotspot IN :hotspots " +
            "GROUP BY p.user.userId")
    List<Object[]> countCompletedHotspotsRaw(
            @Param("users") List<User> users,
            @Param("hotspots") List<Hotspot> hotspots
    );

    @Query("SELECT year(p.firstVisitedAt) AS bucketYear, " +
            "month(p.firstVisitedAt) AS bucketMonth, " +
            "day(p.firstVisitedAt) AS bucketDay, " +
            "COUNT(p) AS total " +
            "FROM UserHotspotProgress p " +
            "WHERE p.firstVisitedAt >= :from AND p.firstVisitedAt < :to " +
            "GROUP BY year(p.firstVisitedAt), month(p.firstVisitedAt), day(p.firstVisitedAt)")
    List<DailyCountProjection> countCheckInsPerDay(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    @Query("SELECT h.hotspotId AS hotspotId, " +
            "h.hotspotName AS hotspotName, " +
            "COUNT(p) AS checkInCount " +
            "FROM UserHotspotProgress p " +
            "JOIN p.hotspot h " +
            "WHERE h.status <> :excludedStatus " +
            "GROUP BY h.hotspotId, h.hotspotName " +
            "ORDER BY COUNT(p) DESC, h.hotspotId ASC")
    List<HotspotCheckInCountProjection> findTopHotspotCheckIns(@Param("excludedStatus") ContentStatus excludedStatus,
                                                               Pageable pageable);
}
