package com.ecgcare.backend.controller;

import com.ecgcare.backend.config.JwtService;
import com.ecgcare.backend.dto.response.AuthResponse;
import com.ecgcare.backend.dto.response.DoctorResponse;
import com.ecgcare.backend.exception.BadRequestException;
import com.ecgcare.backend.exception.GlobalExceptionHandler;
import com.ecgcare.backend.exception.UnauthorizedException;
import com.ecgcare.backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private JwtService jwtService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService, jwtService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerReturns201WithDoctor() throws Exception {
        when(authService.register(any())).thenReturn(
                DoctorResponse.builder().doctorId(doctorId).email("doc@example.com").fullName("Dr. Test").build());

        mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "doc@example.com",
                        "password", "password123",
                        "fullName", "Dr. Test"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value("doc@example.com"));
    }

    @Test
    void registerRejectsInvalidPayloadWith400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "not-an-email",
                        "password", "short",
                        "fullName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verify(authService, never()).register(any());
    }

    @Test
    void registerDuplicateEmailMapsTo400() throws Exception {
        when(authService.register(any())).thenThrow(new BadRequestException("Email already exists"));

        mockMvc.perform(post("/api/auth/register")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "doc@example.com",
                        "password", "password123",
                        "fullName", "Dr. Test"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(any(), any(), any())).thenReturn(
                AuthResponse.builder().accessToken("access").refreshToken("refresh")
                        .expiresIn(900).tokenType("Bearer").sessionId(sessionId).build());

        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .header("User-Agent", "JUnit")
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "doc@example.com",
                        "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void loginWithBadCredentialsMapsTo401() throws Exception {
        when(authService.login(any(), any(), any()))
                .thenThrow(new UnauthorizedException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "doc@example.com",
                        "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void refreshIssuesNewAccessTokenForValidRefreshToken() throws Exception {
        when(jwtService.extractDoctorId("valid-refresh")).thenReturn(doctorId);
        when(jwtService.extractSessionId("valid-refresh")).thenReturn(sessionId);
        when(jwtService.validateToken("valid-refresh")).thenReturn(true);
        when(jwtService.extractEmail("valid-refresh")).thenReturn("doc@example.com");
        when(jwtService.generateAccessToken(doctorId, "doc@example.com", sessionId)).thenReturn("new-access");

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "valid-refresh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    void refreshRejectsExpiredToken() throws Exception {
        when(jwtService.extractDoctorId("expired")).thenReturn(doctorId);
        when(jwtService.extractSessionId("expired")).thenReturn(sessionId);
        when(jwtService.validateToken("expired")).thenReturn(false);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "expired"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRejectsUnparseableToken() throws Exception {
        when(jwtService.extractDoctorId("garbage")).thenThrow(new RuntimeException("bad token"));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "garbage"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutWithBearerTokenEndsSession() throws Exception {
        when(jwtService.extractDoctorId("token")).thenReturn(doctorId);
        when(jwtService.extractSessionId("token")).thenReturn(sessionId);

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());

        verify(authService).logout(sessionId, doctorId);
    }

    @Test
    void logoutWithoutHeaderStillReturns200() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk());

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void meReturnsCurrentDoctor() throws Exception {
        when(authService.getCurrentUser(doctorId)).thenReturn(
                DoctorResponse.builder().doctorId(doctorId).email("doc@example.com").build());

        mockMvc.perform(get("/api/auth/me")
                .principal(new UsernamePasswordAuthenticationToken(doctorId.toString(), sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("doc@example.com"));
    }
}
