package com.edubase.user.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class InternalAuthServiceClientConfig {

    @Bean
    public RestClient internalAuthServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.internal.auth-service-base-url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
