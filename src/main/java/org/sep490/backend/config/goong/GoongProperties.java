package org.sep490.backend.config.goong;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "goong")
public class GoongProperties {
    private String apiKey;
    private String baseUrl;
}
