package com.ecgcare.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MLPropertiesAndRestTemplateTest {

    @Test
    void predictUrlJoinsServiceUrlAndEndpoint() {
        MLProperties properties = new MLProperties();
        assertThat(properties.getPredictUrl()).isEqualTo("http://localhost:8000/predict");

        properties.setServiceUrl("https://ml.example.com");
        properties.setPredictEndpoint("/api/predict");
        assertThat(properties.getPredictUrl()).isEqualTo("https://ml.example.com/api/predict");
    }

    @Test
    void mlPropertiesHaveSaneDefaults() {
        MLProperties properties = new MLProperties();

        assertThat(properties.getMaxRetries()).isEqualTo(3);
        assertThat(properties.getRetryDelaySeconds()).isEqualTo(2);
        assertThat(properties.getMaxImageSizeBytes()).isEqualTo(10 * 1024 * 1024);
        assertThat(properties.getConnectTimeoutSeconds()).isEqualTo(5);
        assertThat(properties.getReadTimeoutSeconds()).isEqualTo(60);
    }

    @Test
    void restTemplateBeanIsConfigured() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(new RestTemplateBuilder());
        assertThat(restTemplate).isNotNull();
        assertThat(restTemplate.getRequestFactory()).isNotNull();
    }
}
