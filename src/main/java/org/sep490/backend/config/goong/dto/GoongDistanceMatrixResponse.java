package org.sep490.backend.config.goong.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micrometer.core.instrument.MultiGauge;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoongDistanceMatrixResponse {
    private List<Row> rows;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        private List<Element> elements;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Element {
        private ValueText distance; // meters
        private ValueText duration; // seconds
        private String status;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValueText {
        private String text;
        private Long value;
    }
}
