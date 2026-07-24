package com.ecgcare.backend.service;

import com.ecgcare.backend.config.JwtProperties;
import com.ecgcare.backend.config.JwtService;
import com.ecgcare.backend.dto.request.LoginRequest;
import com.ecgcare.backend.dto.request.RegisterRequest;
import com.ecgcare.backend.dto.response.AuthResponse;
import com.ecgcare.backend.dto.response.DoctorResponse;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.DoctorAuth;
import com.ecgcare.backend.entity.DoctorCrypto;
import com.ecgcare.backend.entity.Session;
import com.ecgcare.backend.exception.BadRequestException;
import com.ecgcare.backend.exception.UnauthorizedException;
import com.ecgcare.backend.repository.DoctorAuthRepository;
import com.ecgcare.backend.repository.DoctorCryptoRepository;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Low iteration count keeps PBKDF2 fast in tests; login reads the count
    // from kekParams, so the service honours it.
    private static final int TEST_KEK_ITERATIONS = 1000;
    private static final String PASSWORD = "correct-horse-battery";

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private DoctorAuthRepository doctorAuthRepository;
    @Mock
    private DoctorCryptoRepository doctorCryptoRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    private EncryptionService encryptionService;
    private DoctorKeyCache doctorKeyCache;
    private JwtService jwtService;
    private AuthService authService;

    private final UUID doctorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService();
        doctorKeyCache = new DoctorKeyCache();
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-that-is-long-enough-for-hmac-sha256-signing");
        jwtService = new JwtService(jwtProperties);

        authService = new AuthService(doctorRepository, doctorAuthRepository, doctorCryptoRepository,
                sessionRepository, passwordEncoder, jwtService, auditService, encryptionService, doctorKeyCache);
    }

    private Doctor activeDoctor() {
        return Doctor.builder()
                .doctorId(doctorId)
                .email("doc@example.com")
                .fullName("Dr. Test")
                .isActive(true)
                .build();
    }

    // Builds the DoctorCrypto record exactly the way register() would,
    // so login() can genuinely unlock it with the password.
    private DoctorCrypto cryptoFor(String password) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        SecretKey kek = encryptionService.deriveKEK(password.toCharArray(), salt, TEST_KEK_ITERATIONS);
        EncryptionService.EncryptedData enc = encryptionService.encrypt(keyPair.getPrivate().getEncoded(), kek);

        Map<String, Object> kekParams = new HashMap<>();
        kekParams.put("kdfIterations", TEST_KEK_ITERATIONS);

        return DoctorCrypto.builder()
                .doctorId(doctorId)
                .publicKey(keyPair.getPublic().getEncoded())
                .privateKeyEnc(encryptionService.packEncrypted(enc))
                .privateKeySalt(salt)
                .kekParams(kekParams)
                .build();
    }

    // ---------- register ----------

    @Test
    void registerCreatesDoctorAuthAndCrypto() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("New.Doctor@Example.com");
        request.setPassword(PASSWORD);
        request.setFullName("Dr. New");
        request.setPhone("12345");

        when(doctorRepository.existsByEmailIgnoreCase("New.Doctor@Example.com")).thenReturn(false);
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setDoctorId(doctorId);
            return d;
        });
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed-password");

        DoctorResponse response = authService.register(request);

        assertThat(response.getDoctorId()).isEqualTo(doctorId);
        assertThat(response.getEmail()).isEqualTo("new.doctor@example.com");
        assertThat(response.getFullName()).isEqualTo("Dr. New");
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getMfaEnabled()).isFalse();

        ArgumentCaptor<DoctorAuth> authCaptor = ArgumentCaptor.forClass(DoctorAuth.class);
        verify(doctorAuthRepository).save(authCaptor.capture());
        assertThat(authCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");

        ArgumentCaptor<DoctorCrypto> cryptoCaptor = ArgumentCaptor.forClass(DoctorCrypto.class);
        verify(doctorCryptoRepository).save(cryptoCaptor.capture());
        DoctorCrypto crypto = cryptoCaptor.getValue();
        assertThat(crypto.getPublicKey()).isNotEmpty();
        assertThat(crypto.getPrivateKeyEnc()).isNotEmpty();
        assertThat(crypto.getPrivateKeySalt()).hasSize(16);
        assertThat(crypto.getKekParams()).containsEntry("kdfIterations", EncryptionService.KEK_ITERATIONS);

        verify(auditService).logAction(eq("register"), eq("doctor"), eq(doctorId), eq(doctorId), any(), any());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);
        request.setFullName("Dr. Dup");

        when(doctorRepository.existsByEmailIgnoreCase("doc@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Email already exists");
        verify(doctorRepository, never()).save(any());
    }

    // ---------- login ----------

    private void stubHappyLogin(DoctorCrypto crypto, UUID sessionId) {
        Doctor doctor = activeDoctor();
        DoctorAuth auth = DoctorAuth.builder().doctorId(doctorId).doctor(doctor)
                .passwordHash("hashed-password").build();

        when(doctorRepository.findByEmailIgnoreCase("doc@example.com")).thenReturn(Optional.of(doctor));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.of(auth));
        when(passwordEncoder.matches(PASSWORD, "hashed-password")).thenReturn(true);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setSessionId(sessionId);
            return s;
        });
        when(doctorCryptoRepository.findById(doctorId)).thenReturn(Optional.of(crypto));
    }

    @Test
    void loginReturnsValidTokensAndCachesPrivateKey() throws Exception {
        UUID sessionId = UUID.randomUUID();
        stubHappyLogin(cryptoFor(PASSWORD), sessionId);

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);

        AuthResponse response = authService.login(request, "127.0.0.1", "JUnit-agent");

        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(jwtService.extractDoctorId(response.getAccessToken())).isEqualTo(doctorId);
        assertThat(jwtService.extractSessionId(response.getAccessToken())).isEqualTo(sessionId);
        assertThat(jwtService.extractDoctorId(response.getRefreshToken())).isEqualTo(doctorId);

        // The decrypted private key must now be available for this session
        assertThat(doctorKeyCache.get(sessionId)).isPresent();

        verify(auditService).logAction(eq("login"), eq("session"), eq(sessionId), eq(doctorId), eq(sessionId), any());
    }

    @Test
    void loginWithNullIpStillSucceeds() throws Exception {
        UUID sessionId = UUID.randomUUID();
        stubHappyLogin(cryptoFor(PASSWORD), sessionId);

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);

        AuthResponse response = authService.login(request, null, null);
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(doctorRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void loginRejectsMissingAuthRecord() {
        when(doctorRepository.findByEmailIgnoreCase("doc@example.com"))
                .thenReturn(Optional.of(activeDoctor()));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void loginRejectsInactiveAccount() {
        Doctor inactive = activeDoctor();
        inactive.setIsActive(false);
        when(doctorRepository.findByEmailIgnoreCase("doc@example.com")).thenReturn(Optional.of(inactive));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.of(
                DoctorAuth.builder().doctorId(doctorId).passwordHash("hashed-password").build()));

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Account is inactive");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(doctorRepository.findByEmailIgnoreCase("doc@example.com"))
                .thenReturn(Optional.of(activeDoctor()));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.of(
                DoctorAuth.builder().doctorId(doctorId).passwordHash("hashed-password").build()));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword("wrong-password");

        assertThatThrownBy(() -> authService.login(request, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid credentials");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void loginFailsWhenCryptoRecordMissing() {
        Doctor doctor = activeDoctor();
        when(doctorRepository.findByEmailIgnoreCase("doc@example.com")).thenReturn(Optional.of(doctor));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.of(
                DoctorAuth.builder().doctorId(doctorId).passwordHash("hashed-password").build()));
        when(passwordEncoder.matches(PASSWORD, "hashed-password")).thenReturn(true);
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setSessionId(UUID.randomUUID());
            return s;
        });
        when(doctorCryptoRepository.findById(doctorId)).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Failed to unlock encryption keys");
    }

    @Test
    void loginFailsWhenKeyWasEncryptedWithDifferentPassword() throws Exception {
        // Simulates crypto material that this password cannot unlock
        UUID sessionId = UUID.randomUUID();
        stubHappyLogin(cryptoFor("a-completely-different-password"), sessionId);

        LoginRequest request = new LoginRequest();
        request.setEmail("doc@example.com");
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> authService.login(request, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Failed to unlock encryption keys");
        assertThat(doctorKeyCache.get(sessionId)).isEmpty();
    }

    // ---------- logout ----------

    @Test
    void logoutClosesOwnSessionAndEvictsKey() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Session session = Session.builder().sessionId(sessionId).doctor(activeDoctor()).build();
        when(sessionRepository.findBySessionIdAndLogoutAtIsNull(sessionId)).thenReturn(Optional.of(session));
        doctorKeyCache.put(sessionId, KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate());

        authService.logout(sessionId, doctorId);

        verify(sessionRepository).logoutSession(eq(sessionId), any(), eq(Session.SessionEndReason.logout));
        verify(auditService).logAction(eq("logout"), eq("session"), eq(sessionId), eq(doctorId), eq(sessionId), any());
        assertThat(doctorKeyCache.get(sessionId)).isEmpty();
    }

    @Test
    void logoutOfUnknownSessionStillEvictsCache() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findBySessionIdAndLogoutAtIsNull(sessionId)).thenReturn(Optional.empty());
        doctorKeyCache.put(sessionId, KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate());

        authService.logout(sessionId, doctorId);

        verify(sessionRepository, never()).logoutSession(any(), any(), any());
        assertThat(doctorKeyCache.get(sessionId)).isEmpty();
    }

    @Test
    void logoutDoesNotCloseAnotherDoctorsSession() {
        UUID sessionId = UUID.randomUUID();
        Doctor otherDoctor = Doctor.builder().doctorId(UUID.randomUUID()).email("other@example.com")
                .fullName("Dr. Other").isActive(true).build();
        Session session = Session.builder().sessionId(sessionId).doctor(otherDoctor).build();
        when(sessionRepository.findBySessionIdAndLogoutAtIsNull(sessionId)).thenReturn(Optional.of(session));

        authService.logout(sessionId, doctorId);

        verify(sessionRepository, never()).logoutSession(any(), any(), any());
    }

    // ---------- getCurrentUser ----------

    @Test
    void getCurrentUserReturnsProfileWithMfaFlag() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(activeDoctor()));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.of(
                DoctorAuth.builder().doctorId(doctorId).passwordHash("x").mfaEnabled(true).build()));

        DoctorResponse response = authService.getCurrentUser(doctorId);

        assertThat(response.getDoctorId()).isEqualTo(doctorId);
        assertThat(response.getEmail()).isEqualTo("doc@example.com");
        assertThat(response.getMfaEnabled()).isTrue();
    }

    @Test
    void getCurrentUserWithoutAuthRecordReportsMfaDisabled() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(activeDoctor()));
        when(doctorAuthRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThat(authService.getCurrentUser(doctorId).getMfaEnabled()).isFalse();
    }

    @Test
    void getCurrentUserThrowsWhenDoctorMissing() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUser(doctorId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Doctor not found");
    }
}
