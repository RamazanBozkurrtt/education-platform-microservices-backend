package com.edubase.course.recommendation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationServiceProperties {

    private Service service = new Service();
    private int candidateLimit = 150;
    private int defaultPreferredDurationSeconds = 5400;
    private int recentCourseLimit = 5;
    private double dropoutLowProgressThresholdPercent = 25.0d;

    @Data
    public static class Service {
        private String baseUrl;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
    }
}
