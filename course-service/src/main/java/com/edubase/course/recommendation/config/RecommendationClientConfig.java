package com.edubase.course.recommendation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RecommendationServiceProperties.class)
public class RecommendationClientConfig {

    @Bean
    public RestClient recommendationRestClient(
            RestClient.Builder builder,
            RecommendationServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getService().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getService().getReadTimeoutMs());

        return builder
                .baseUrl(properties.getService().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
