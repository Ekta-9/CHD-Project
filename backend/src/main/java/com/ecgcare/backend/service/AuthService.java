package com.ecgcare.backend.service;

import com.ecgcare.backend.config.JwtService;
import com.ecgcare.backend.dto.request.LoginRequest;
import com.ecgcare.backend.dto.request.RegisterRequest;
import com.ecgcare.backend.dto.response.AuthResponse;
import com.ecgcare.backend.dto.response.DoctorResponse;
import com.ecgcare.backend.entity.*;
import com.ecgcare.backend.exception.BadRequestException;
import com.ecgcare.backend.exception.UnauthorizedException;
import com.ecgcare.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final DoctorRepository doctorRepository;
    private final DoctorAuthRepository doctorAuthRepository;
    private final DoctorCryptoRepository doctorCryptoRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final EncryptionService encryptionService;

    @Transactional
    public DoctorResponse register(RegisterRequest request) {
        if (doctorRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new BadRequestException("Email already exists");
        }

        // Create doctor
        Doctor doctor = Doctor.builder()
                .email(request.getEmail().toLowerCase())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isActive(true)
                .build();
        doctor = doctorRepository.save(doctor);

        // Create auth
        DoctorAuth doctorAuth = DoctorAuth.builder()
                .doctor(doctor)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .mfaEnabled(false)
                .build();
        doctorAuthRepository.save(doctorAuth);

        // Generate RSA key pair (simplified - using AES keys for now)
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Encrypt private key with password (simplified - storing raw bytes for now)
            // In production, this should use proper encryption with the user's password
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);

            Map<String, Object> kekParams = new HashMap<>();
            kekParams.put("algorithm", "RSA");
            kekParams.put("keySize", 2048);

            DoctorCrypto doctorCrypto = DoctorCrypto.builder()
                    .doctor(doctor)
                    .publicKey(keyPair.getPublic().getEncoded())
                    .privateKeyEnc(privateKeyBytes)
                    .privateKeySalt(salt)
                    .kekParams(kekParams)
                    .build();
            doctorCryptoRepository.save(doctorCrypto);
        } catch (Exception e) {
            log.error("Failed to generate crypto keys", e);
            throw new BadRequestException("Failed to generate cryptographic keys");
        }

        auditService.logAction("register", "doctor", doctor.getDoctorId(), doctor.getDoctorId(), null, null);

        return DoctorResponse.builder()
                .doctorId(doctor.getDoctorId())
                .email(doctor.getEmail())
                .fullName(doctor.getFullName())
                .isActive(doctor.getIsActive())
                .mfaEnabled(false)
                .createdAt(doctor.getCreatedAt())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        Doctor doctor = doctorRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        DoctorAuth doctorAuth = doctorAuthRepository.findById(doctor.getDoctorId())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!doctor.getIsActive()) {
            throw new UnauthorizedException("Account is inactive");
        }

        if (!passwordEncoder.matches(request.getPassword(), doctorAuth.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // Create session
        Session session = Session.builder()
                .doctor(doctor)
                .loginAt(OffsetDateTime.now())
                .lastActivityAt(OffsetDateTime.now())
                .build();

        try {
            if (ipAddress != null) {
                session.setIp(InetAddress.getByName(ipAddress));
            }
        } catch (Exception e) {
            log.warn("Failed to parse IP address: {}", ipAddress);
        }
        session.setUserAgent(userAgent);
        session = sessionRepository.save(session);

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(doctor.getDoctorId(), doctor.getEmail(),
                session.getSessionId());
        String refreshToken = jwtService.generateRefreshToken(doctor.getDoctorId(), session.getSessionId());

        auditService.logAction("login", "session", session.getSessionId(), doctor.getDoctorId(), session.getSessionId(),
                null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(900) // 15 minutes
                .tokenType("Bearer")
                .sessionId(session.getSessionId())
                .build();
    }

    @Transactional
    public void logout(UUID sessionId, UUID doctorId) {
        Session session = sessionRepository.findBySessionIdAndLogoutAtIsNull(sessionId)
                .orElse(null);

        if (session != null && session.getDoctor().getDoctorId().equals(doctorId)) {
            sessionRepository.logoutSession(sessionId, OffsetDateTime.now(), Session.SessionEndReason.logout);
            auditService.logAction("logout", "session", sessionId, doctorId, sessionId, null);
        }
    }

    public DoctorResponse getCurrentUser(UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new BadRequestException("Doctor not found"));

        DoctorAuth doctorAuth = doctorAuthRepository.findById(doctorId).orElse(null);

        return DoctorResponse.builder()
                .doctorId(doctor.getDoctorId())
                .email(doctor.getEmail())
                .fullName(doctor.getFullName())
                .phone(doctor.getPhone())
                .isActive(doctor.getIsActive())
                .mfaEnabled(doctorAuth != null && doctorAuth.getMfaEnabled())
                .createdAt(doctor.getCreatedAt())
                .build();
    }
}
