       package org.sep490.backend;

import org.junit.jupiter.api.Test;
import org.sep490.backend.config.security.DotEnvConfig;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BackEndApplicationTests {

    static {
        DotEnvConfig.loadEnv();
    }

    @Test
    void contextLoads() {
    }

}
