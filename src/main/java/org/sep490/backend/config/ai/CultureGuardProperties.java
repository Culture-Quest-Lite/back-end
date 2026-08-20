package org.sep490.backend.config.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.culture-guard")
public class CultureGuardProperties {

    private boolean enabled = true;
    private double passThreshold = 0.60;
    private double rejectThreshold = 0.35;
    private int storyMaxChars = 1500;
    private int tagMinAllowHits = 1;
    private int storyMinAllowHits = 3;
    private String lexiconPath = "classpath:culture/culture-lexicon.yml";
}
