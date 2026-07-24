package com.ecgcare.backend.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtProperties properties;
    private JwtAuthenticationFilter filter;
    private final UUID doctorId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha256-signing");
        jwtService = new JwtService(properties);
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void runFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void noAuthorizationHeaderLeavesContextEmpty() throws Exception {
        runFilter(new MockHttpServletRequest());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerHeaderLeavesContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        runFilter(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidTokenLeavesContextEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer garbage-token");
        runFilter(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredTokenLeavesContextEmpty() throws Exception {
        properties.setAccessTtlMinutes(-5);
        String expired = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);
        properties.setAccessTtlMinutes(15);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + expired);
        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validTokenPopulatesAuthenticationWithDoctorAndSession() throws Exception {
        String token = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        runFilter(request);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(doctorId);
        assertThat(auth.getCredentials()).isEqualTo(sessionId);
        assertThat(auth.getAuthorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_DOCTOR");
    }

    @Test
    void existingAuthenticationIsNotOverwritten() throws Exception {
        String token = jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId);
        Authentication existing = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "already-authenticated", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        runFilter(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
