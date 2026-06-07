package org.sep490.backend;

import org.sep490.backend.config.security.DotEnvConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class BackEndApplication {
    public static void main(String[] args) {
        DotEnvConfig.loadEnv();
        SpringApplication.run(BackEndApplication.class, args);
    }

    @Bean
    public CommandLineRunner init(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;");
            } catch (Exception e) {
                // Ignore
            }
            try {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM levels", Integer.class);
                if (count != null && count == 0) {
                    jdbcTemplate.execute("INSERT INTO levels (name, req_xp, description, created_at, updated_at) " +
                            "VALUES ('Level 1', 0, 'Cấp độ khởi đầu', NOW(), NOW());");
                }
            } catch (Exception e) {
                // Ignore
            }
        };
    }
}
