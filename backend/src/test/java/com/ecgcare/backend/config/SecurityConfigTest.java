package com.ecgcare.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(mock(JwtAuthenticationFilter.class));
    }

    private CorsConfiguration corsFor(String allowedOrigins) {
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigins", allowedOrigins);
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) securityConfig
                .corsConfigurationSource();
        return source.getCorsConfigurations().get("/**");
    }

    @Test
    void corsParsesCommaSeparatedOrigins() {
        CorsConfiguration config = corsFor("https://app.example.com, https://staging.example.com");

        assertThat(config.getAllowedOrigins())
                .containsExactly("https://app.example.com", "https://staging.example.com");
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(config.getExposedHeaders()).contains("Authorization");
    }

    @Test
    void corsWildcardAllowsAllOrigins() {
        assertThat(corsFor("*").getAllowedOrigins()).containsExactly("*");
    }

    @Test
    void corsNullFallsBackToLocalhost() {
        assertThat(corsFor(null).getAllowedOrigins()).containsExactly("http://localhost:3000");
    }

    @Test
    void corsIgnoresEmptyEntries() {
        assertThat(corsFor("https://app.example.com,, ").getAllowedOrigins())
                .containsExactly("https://app.example.com");
    }

    @Test
    void passwordEncoderUsesBcryptAndVerifies() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("password123");

        assertThat(hash).startsWith("$2");
        assertThat(encoder.matches("password123", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
