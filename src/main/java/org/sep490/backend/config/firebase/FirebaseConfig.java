package org.sep490.backend.config.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.service-account-path}")
    private String serviceAccountPath;

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized, skipping.");
            return;
        }
        try (InputStream serviceAccount = resolveServiceAccount()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("FirebaseApp initialized successfully.");
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage(), e);
        }
    }

    private InputStream resolveServiceAccount() throws IOException {
        if (serviceAccountPath.startsWith("classpath:")) {
            String classpathLocation = serviceAccountPath.substring("classpath:".length());
            return new ClassPathResource(classpathLocation).getInputStream();
        }
        return new FileInputStream(serviceAccountPath);
    }
}

