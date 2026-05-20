package com.edubase.review.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CourseServiceClientConfig {

    @Bean
    public RestClient courseServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.internal.course-service-base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
