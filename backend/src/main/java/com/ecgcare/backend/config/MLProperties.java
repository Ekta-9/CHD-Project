package com.ecgcare.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ml")
public class MLProperties {
    private String serviceUrl = "http://localhost:8000";
    private String predictEndpoint = "/predict";
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 60;
    private int maxRetries = 3;
    private int retryDelaySeconds = 2;
    private long maxImageSizeBytes = 10 * 1024 * 1024; // 10MB default

    public String getPredictUrl() {
        return serviceUrl + predictEndpoint;
    }
}




