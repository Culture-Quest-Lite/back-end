package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.PartnerSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartnerSubscriptionRepository extends JpaRepository<PartnerSubscription, Long> {
    List<PartnerSubscription> findByPartner_UserId(Long partnerId);
    List<PartnerSubscription> findByPartner_UserIdOrderByCreatedAtDesc(Long partnerId);

    @Query(value = "SELECT EXISTS (" +
            "  SELECT 1 FROM country_boundaries cb " +
            "  WHERE cb.country_name = 'Vietnam' " +
            "  AND ST_Within(" +
            "      ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), " +
            "      cb.geom" +
            "  )" +
            ")", nativeQuery = true)
    boolean isLocationInVietnam(@Param("longitude") Double longitude, @Param("latitude") Double latitude);
}
