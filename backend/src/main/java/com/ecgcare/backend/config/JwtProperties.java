package com.ecgcare.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String issuer = "ecgcare";
    private String secret;
    private Integer accessTtlMinutes = 15;
    private Integer refreshTtlDays = 7;
}