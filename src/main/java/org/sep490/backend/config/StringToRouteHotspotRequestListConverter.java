package org.sep490.backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.module.content.dto.request.RouteHotspotRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StringToRouteHotspotRequestListConverter implements Converter<String, List<RouteHotspotRequest>> {

    private final ObjectMapper objectMapper;

    @Override
    public List<RouteHotspotRequest> convert(String source) {
        if (source == null || source.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(source, new TypeReference<List<RouteHotspotRequest>>() {});
        } catch (Exception e) {
            log.error("Failed to convert JSON string to List<RouteHotspotRequest>: {}", source, e);
            throw new IllegalArgumentException("Dữ liệu hotspots không hợp lệ: " + e.getMessage());
        }
    }
}
