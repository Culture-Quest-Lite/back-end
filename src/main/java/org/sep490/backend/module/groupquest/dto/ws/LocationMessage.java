package org.sep490.backend.module.groupquest.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationMessage {
    private Long userId;
    private String displayName;
    private Double latitude;
    private Double longitude;
    private LocalDateTime timestamp;
}
