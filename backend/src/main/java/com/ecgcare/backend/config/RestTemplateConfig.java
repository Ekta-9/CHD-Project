package com.ecgcare.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Configure timeouts for ML service calls
        // Connection timeout: 5 seconds (time to establish connection)
        // Read timeout: 60 seconds (time to wait for response - ML inference can take
        // time)
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(60000); // 60 seconds

        return builder
                .requestFactory(() -> factory)
                .build();
    }
}
