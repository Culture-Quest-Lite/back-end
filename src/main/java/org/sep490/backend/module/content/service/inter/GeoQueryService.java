package org.sep490.backend.module.content.service.inter;

import org.sep490.backend.module.content.entity.Hotspot;

import java.util.List;

public interface GeoQueryService {

    boolean isLocationInVietnam(Double longitude, Double latitude);

    List<Hotspot> findNearby(double longitude, double latitude, double radiusInMeters, String status);

    void evictNearby();

    String parseGeoJsonToWkt(String geoJson);

    String findBoundaryGeoJson(Long hotspotId);
}
