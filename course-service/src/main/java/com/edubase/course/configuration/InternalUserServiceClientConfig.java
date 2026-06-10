package com.edubase.course.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class InternalUserServiceClientConfig {

    @Bean
    public RestClient internalUserServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.internal.user-service-base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
