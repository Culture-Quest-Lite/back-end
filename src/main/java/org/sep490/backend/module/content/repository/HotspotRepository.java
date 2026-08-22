package org.sep490.backend.module.content.repository;

import org.sep490.backend.module.admin.dto.projection.HotspotPublishSummaryProjection;
import org.sep490.backend.module.content.entity.Hotspot;
import org.sep490.backend.module.content.entity.Story;
import org.sep490.backend.module.content.entity.enumeration.ContentStatus;
import org.sep490.backend.module.content.entity.enumeration.ContentType;
import org.sep490.backend.module.curator.dto.projection.ContentStatusCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HotspotRepository extends JpaRepository<Hotspot, Long>, JpaSpecificationExecutor<Hotspot> {
    @Query(value = "SELECT * FROM hotspots h " +
            "WHERE ST_DWithin(" +
            "    CAST(h.location AS geography), " +
            "    CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography), " +
            "    :radiusInMeters" +
            ") " +
            "AND h.status = :status",
            nativeQuery = true)
    List<Hotspot> findNearbyHotspotsWithStatus(
            @Param("lon") double lon,
            @Param("lat") double lat,
            @Param("radiusInMeters") double radiusInMeters,
            @Param("status") String status
            //@Param("excludeId") Long excludeId
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

    @Query(value = "SELECT ST_DWithin(" +
            "    CAST(h.boundary AS geography), " +
            "    CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography), " +
            "    :toleranceMeters" +
            ") " +
            "FROM hotspots h WHERE h.hotspot_id = :hotspotId AND h.boundary IS NOT NULL",
            nativeQuery = true)
    Boolean isWithinBoundary(@Param("hotspotId") Long hotspotId,
                             @Param("lon") double lon,
                             @Param("lat") double lat,
                             @Param("toleranceMeters") double toleranceMeters);

    // Khoảng cách tới mép polygon, dùng cho thông báo lỗi động.
    @Query(value = "SELECT ST_Distance(" +
            "    CAST(h.boundary AS geography), " +
            "    CAST(ST_SetSRID(ST_MakePoint(:lon, :lat), 4326) AS geography)" +
            ") " +
            "FROM hotspots h WHERE h.hotspot_id = :hotspotId AND h.boundary IS NOT NULL",
            nativeQuery = true)
    Double distanceToBoundary(@Param("hotspotId") Long hotspotId,
                              @Param("lon") double lon,
                              @Param("lat") double lat);

    // Dự án chỉ có jts-core (không có jts-io-common nên không dùng được
    // GeoJsonReader). Nhờ PostGIS parse GeoJSON rồi trả WKT để WKTReader dựng lại.
    // Ném DataAccessException khi GeoJSON sai định dạng.
    @Query(value = "SELECT ST_AsText(ST_SetSRID(ST_GeomFromGeoJSON(CAST(:geoJson AS text)), 4326))",
            nativeQuery = true)
    String parseGeoJsonToWkt(@Param("geoJson") String geoJson);

    @Query(value = "SELECT ST_AsGeoJSON(h.boundary) FROM hotspots h WHERE h.hotspot_id = :hotspotId",
            nativeQuery = true)
    String findBoundaryGeoJson(@Param("hotspotId") Long hotspotId);

    List<Hotspot> findByStatus(ContentStatus status);

    @Query("SELECT COUNT(h) AS totalPublished, " +
            "COALESCE(SUM(CASE WHEN COALESCE(h.publishedAt, h.updatedAt) >= :weekStart THEN 1L ELSE 0L END), 0L) AS publishedThisWeek " +
            "FROM Hotspot h " +
            "WHERE h.status = :status")
    HotspotPublishSummaryProjection summarizePublishedHotspots(@Param("status") ContentStatus status,
                                                               @Param("weekStart") LocalDateTime weekStart);

    @Query("SELECT h.status AS status, COUNT(h) AS total FROM Hotspot h " +
            "WHERE h.status <> :excludedStatus " +
            "GROUP BY h.status")
    List<ContentStatusCountProjection> countHotspotsByStatus(@Param("excludedStatus") ContentStatus excludedStatus);

    @Query("SELECT h.createdBy.userId FROM Hotspot h WHERE h.hotspotId = :id")
    Optional<Long> findOwnerId(@Param("id") Long id);

    List<Hotspot> findByContentTypeAndValidToBefore(ContentType contentType, LocalDateTime validToBefore);
}
