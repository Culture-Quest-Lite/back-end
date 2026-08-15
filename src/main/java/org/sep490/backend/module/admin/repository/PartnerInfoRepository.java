package org.sep490.backend.module.admin.repository;

import org.sep490.backend.module.admin.entity.PartnerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerInfoRepository extends JpaRepository<PartnerInfo, Long>, JpaSpecificationExecutor<PartnerInfo> {
    Optional<PartnerInfo> findByUser_UserId(Long userId);
    boolean existsByShopEmail(String shopEmail);

    @Query("SELECT p FROM PartnerInfo p WHERE p.user.userId = :userId OR p.shopAccount.userId = :userId")
    List<PartnerInfo> findByOwnerOrShopAccount(@Param("userId") Long userId);

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
