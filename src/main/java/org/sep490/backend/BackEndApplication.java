package org.sep490.backend;

import org.sep490.backend.config.security.DotEnvConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackEndApplication {
    public static void main(String[] args) {
        DotEnvConfig.loadEnv();
        SpringApplication.run(BackEndApplication.class, args);
    }
}
