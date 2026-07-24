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

import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.OffsetDateTime;
import java.util.Arrays;
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
    private final DoctorKeyCache doctorKeyCache;

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

        // Generate RSA key pair, then encrypt the private key with a key derived
        // from the doctor's password (PBKDF2) so it's never stored readable.
        char[] passwordChars = request.getPassword().toCharArray();
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);

            SecretKey kek = encryptionService.deriveKEK(passwordChars, salt, EncryptionService.KEK_ITERATIONS);
            EncryptionService.EncryptedData encryptedPrivateKey = encryptionService.encrypt(privateKeyBytes, kek);
            byte[] packedPrivateKey = encryptionService.packEncrypted(encryptedPrivateKey);

            Map<String, Object> kekParams = new HashMap<>();
            kekParams.put("kdf", "PBKDF2WithHmacSHA256");
            kekParams.put("kdfIterations", EncryptionService.KEK_ITERATIONS);
            kekParams.put("keyAlgorithm", "RSA");
            kekParams.put("keySize", 2048);

            DoctorCrypto doctorCrypto = DoctorCrypto.builder()
                    .doctor(doctor)
                    .publicKey(keyPair.getPublic().getEncoded())
                    .privateKeyEnc(packedPrivateKey)
                    .privateKeySalt(salt)
                    .kekParams(kekParams)
                    .build();
            doctorCryptoRepository.save(doctorCrypto);
        } catch (Exception e) {
            log.error("Failed to generate crypto keys", e);
            throw new BadRequestException("Failed to generate cryptographic keys");
        } finally {
            Arrays.fill(passwordChars, '\0');
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

        // Decrypt the doctor's private key using their password and hold it in
        // memory for this session only, so patient records can be unwrapped
        // later without asking for the password again on every request.
        char[] passwordChars = request.getPassword().toCharArray();
        try {
            DoctorCrypto doctorCrypto = doctorCryptoRepository.findById(doctor.getDoctorId())
                    .orElseThrow(() -> new UnauthorizedException("Doctor crypto not found"));

            int iterations = ((Number) doctorCrypto.getKekParams()
                    .getOrDefault("kdfIterations", EncryptionService.KEK_ITERATIONS)).intValue();
            SecretKey kek = encryptionService.deriveKEK(passwordChars, doctorCrypto.getPrivateKeySalt(), iterations);

            EncryptionService.EncryptedData packedPrivateKey = encryptionService.unpackEncrypted(doctorCrypto.getPrivateKeyEnc());
            byte[] privateKeyBytes = encryptionService.decrypt(packedPrivateKey, kek);
            doctorKeyCache.put(session.getSessionId(), encryptionService.privateKeyFromBytes(privateKeyBytes));
        } catch (Exception e) {
            log.error("Failed to unlock encryption keys for doctor {}", doctor.getDoctorId(), e);
            throw new UnauthorizedException("Failed to unlock encryption keys");
        } finally {
            Arrays.fill(passwordChars, '\0');
        }

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
        doctorKeyCache.evict(sessionId);
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
