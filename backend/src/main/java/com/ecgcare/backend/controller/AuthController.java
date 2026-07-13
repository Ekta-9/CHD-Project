package com.ecgcare.backend.controller;

import com.ecgcare.backend.config.JwtService;
import com.ecgcare.backend.dto.request.LoginRequest;
import com.ecgcare.backend.dto.request.RefreshTokenRequest;
import com.ecgcare.backend.dto.request.RegisterRequest;
import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.AuthResponse;
import com.ecgcare.backend.dto.response.DoctorResponse;
import com.ecgcare.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<DoctorResponse>> register(@Valid @RequestBody RegisterRequest request) {
        DoctorResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Doctor registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            UUID doctorId = jwtService.extractDoctorId(request.getRefreshToken());
            UUID sessionId = jwtService.extractSessionId(request.getRefreshToken());

            if (!jwtService.validateToken(request.getRefreshToken())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("UNAUTHORIZED", "Invalid or expired refresh token"));
            }

            String email = jwtService.extractEmail(request.getRefreshToken());
            String accessToken = jwtService.generateAccessToken(doctorId, email, sessionId);

            AuthResponse response = AuthResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(900)
                    .tokenType("Bearer")
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid refresh token"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            UUID doctorId = jwtService.extractDoctorId(token);
            UUID sessionId = jwtService.extractSessionId(token);
            authService.logout(sessionId, doctorId);
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<DoctorResponse>> getCurrentUser(Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        DoctorResponse response = authService.getCurrentUser(doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
