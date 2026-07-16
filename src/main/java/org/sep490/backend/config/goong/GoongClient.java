package org.sep490.backend.config.goong;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.sep490.backend.config.goong.dto.GoongDistanceMatrixResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GoongClient {

    GoongProperties properties;
    RestTemplate restTemplate;

    public GoongDistanceMatrixResponse distanceMatrix(List<double[]> origins,
                                                         List<double[]> destinations,
                                                         String vehicle) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getBaseUrl() + "/DistanceMatrix")
                .queryParam("origins", joinPoints(origins, "|"))
                .queryParam("destinations", joinPoints(destinations, "|"))
                .queryParam("vehicle", vehicle == null ? "car" : vehicle)
                .queryParam("api_key", properties.getApiKey())
                .encode()
                .toUriString();

        return restTemplate.getForObject(url, GoongDistanceMatrixResponse.class);
    }

    private String joinPoints(List<double[]> points, String delimiter) {
        return points.stream()
                .map(this::point)
                .collect(Collectors.joining(delimiter));
    }

    private String point(double[] latLng) {
        return latLng[0] + "," + latLng[1];
    }
}
