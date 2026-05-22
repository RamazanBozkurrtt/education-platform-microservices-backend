package com.edubase.course.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class InternalEnrollmentServiceClientConfig {

    @Bean
    public RestClient internalEnrollmentServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.internal.enrollment-service-base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
