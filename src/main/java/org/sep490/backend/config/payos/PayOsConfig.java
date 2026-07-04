package org.sep490.backend.config.payos;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
@RequiredArgsConstructor
public class PayOsConfig {

    private final PayOsProperties properties;

    @Bean
    public PayOS payOS() {
        return new PayOS(
                properties.getClientId(),
                properties.getApiKey(),
                properties.getChecksumKey()
        );
    }
}
