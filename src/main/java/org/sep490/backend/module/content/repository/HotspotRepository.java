package org.sep490.backend.module.content.repository;

import org.locationtech.jts.geom.Point;
import org.sep490.backend.module.content.entity.Hotspot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HotspotRepository extends JpaRepository<Hotspot, Long>, JpaSpecificationExecutor<Hotspot> {
    @Query(value = "SELECT * FROM hotspots h " +
            "WHERE ST_DWithin(" +
            "    CAST(h.location AS geography), " +
            "    CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography), " +
            "    :radiusInMeters" +
            ") " +
            "AND h.hotspot_id != :excludeId",
            nativeQuery = true)
    List<Hotspot> findNearbyHotspots(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusInMeters") double radiusInMeters,
            @Param("excludeId") Long excludeId
    );
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
