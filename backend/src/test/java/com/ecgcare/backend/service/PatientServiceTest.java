package com.ecgcare.backend.service;

import com.ecgcare.backend.dto.request.PatientCreateRequest;
import com.ecgcare.backend.dto.request.PatientUpdateRequest;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.PatientResponse;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.DoctorCrypto;
import com.ecgcare.backend.entity.Patient;
import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.entity.PatientKey;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.exception.UnauthorizedException;
import com.ecgcare.backend.repository.DoctorCryptoRepository;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.PatientAccessRepository;
import com.ecgcare.backend.repository.PatientKeyRepository;
import com.ecgcare.backend.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientKeyRepository patientKeyRepository;
    @Mock
    private PatientAccessRepository patientAccessRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private DoctorCryptoRepository doctorCryptoRepository;
    @Mock
    private AuditService auditService;

    private EncryptionService encryptionService;
    private DoctorKeyCache doctorKeyCache;
    private PatientService patientService;

    private final UUID doctorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private KeyPair doctorKeyPair;

    @BeforeEach
    void setUp() throws Exception {
        encryptionService = new EncryptionService();
        doctorKeyCache = new DoctorKeyCache();
        patientService = new PatientService(patientRepository, patientKeyRepository, patientAccessRepository,
                doctorRepository, doctorCryptoRepository, encryptionService, auditService, doctorKeyCache);

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        doctorKeyPair = keyGen.generateKeyPair();
    }

    private Doctor doctor() {
        return Doctor.builder().doctorId(doctorId).email("doc@example.com")
                .fullName("Dr. Test").isActive(true).build();
    }

    // Builds a Patient + PatientKey pair the same way createPatient would,
    // encrypted for this test's doctor key pair.
    private record EncryptedPatient(Patient patient, PatientKey key) {
    }

    private EncryptedPatient encryptedPatient(Map<String, Object> data) throws Exception {
        EncryptionService.EncryptedDataWithKey enc = encryptionService.encryptJson(data);
        Patient patient = Patient.builder()
                .patientId(patientId)
                .anonymizedCode("PAT-TEST-1234")
                .encPayload(enc.encryptedData().data())
                .encPayloadIv(enc.encryptedData().iv())
                .encPayloadTag(enc.encryptedData().tag())
                .build();

        EncryptionService.EncryptedData wrapped = encryptionService.wrapKey(enc.key(), doctorKeyPair.getPublic());
        PatientKey key = PatientKey.builder()
                .patient(patient)
                .doctor(doctor())
                .wrappingScheme("RSA-OAEP")
                .dekEnc(wrapped.data())
                .dekIv(wrapped.iv())
                .dekTag(wrapped.tag())
                .build();
        return new EncryptedPatient(patient, key);
    }

    // ---------- createPatient ----------

    @Test
    void createPatientEncryptsDataAndGrantsOwnerAccess() {
        PatientCreateRequest request = new PatientCreateRequest();
        request.setPatientData(Map.of("name", "Baby A", "age", 1));

        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setPatientId(patientId);
            return p;
        });
        when(doctorCryptoRepository.findById(doctorId)).thenReturn(Optional.of(
                DoctorCrypto.builder().doctorId(doctorId)
                        .publicKey(doctorKeyPair.getPublic().getEncoded()).build()));

        PatientResponse response = patientService.createPatient(request, doctorId);

        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getAnonymizedCode()).startsWith("PAT-");
        assertThat(response.getAccessRole()).isEqualTo(PatientAccess.AccessRole.owner);
        assertThat(response.getPatientData()).isNull();

        // Stored payload must be encrypted, and DEK must unwrap with the doctor's private key
        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(patientCaptor.capture());
        assertThat(patientCaptor.getValue().getEncPayload()).isNotEmpty();

        ArgumentCaptor<PatientKey> keyCaptor = ArgumentCaptor.forClass(PatientKey.class);
        verify(patientKeyRepository).save(keyCaptor.capture());
        assertThat(keyCaptor.getValue().getWrappingScheme()).isEqualTo("RSA-OAEP");

        ArgumentCaptor<PatientAccess> accessCaptor = ArgumentCaptor.forClass(PatientAccess.class);
        verify(patientAccessRepository).save(accessCaptor.capture());
        assertThat(accessCaptor.getValue().getRole()).isEqualTo(PatientAccess.AccessRole.owner);

        verify(auditService).logAction(eq("create"), eq("patient"), eq(patientId), eq(doctorId), any(), any());
    }

    @Test
    void createPatientFailsWhenDoctorMissing() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        PatientCreateRequest request = new PatientCreateRequest();
        request.setPatientData(Map.of("name", "Baby A"));

        assertThatThrownBy(() -> patientService.createPatient(request, doctorId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createPatientWrapsCryptoFailureInRuntimeException() {
        PatientCreateRequest request = new PatientCreateRequest();
        request.setPatientData(Map.of("name", "Baby A"));

        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(doctorCryptoRepository.findById(doctorId)).thenReturn(Optional.of(
                DoctorCrypto.builder().doctorId(doctorId).publicKey(new byte[] { 1, 2, 3 }).build()));

        assertThatThrownBy(() -> patientService.createPatient(request, doctorId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create patient");
    }

    // ---------- getPatient ----------

    @Test
    void getPatientDecryptsDataForAuthorizedDoctor() throws Exception {
        EncryptedPatient ep = encryptedPatient(Map.of("name", "Baby A", "diagnosis", "VSD"));

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(ep.patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
        when(patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId))
                .thenReturn(Optional.of(ep.key()));
        doctorKeyCache.put(sessionId, doctorKeyPair.getPrivate());

        PatientResponse response = patientService.getPatient(patientId, doctorId, sessionId);

        assertThat(response.getPatientData())
                .containsEntry("name", "Baby A")
                .containsEntry("diagnosis", "VSD");
        assertThat(response.getAccessRole()).isEqualTo(PatientAccess.AccessRole.owner);
        verify(auditService).logAction(eq("read"), eq("patient"), eq(patientId), eq(doctorId), any(), any());
    }

    @Test
    void getPatientThrowsWhenPatientMissing() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatient(patientId, doctorId, sessionId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPatientThrowsWhenDoctorHasNoAccess() throws Exception {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(encryptedPatient(Map.of()).patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatient(patientId, doctorId, sessionId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getPatientThrowsUnauthorizedWhenSessionKeyExpired() throws Exception {
        EncryptedPatient ep = encryptedPatient(Map.of("name", "Baby A"));

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(ep.patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
        when(patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId))
                .thenReturn(Optional.of(ep.key()));
        // No key in the cache: simulates server restart / expired session

        assertThatThrownBy(() -> patientService.getPatient(patientId, doctorId, sessionId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("log in again");
    }

    @Test
    void getPatientThrowsWhenPatientKeyMissing() throws Exception {
        EncryptedPatient ep = encryptedPatient(Map.of("name", "Baby A"));

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(ep.patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
        when(patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getPatient(patientId, doctorId, sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Patient key not found");
    }

    // ---------- listPatients ----------

    @Test
    void listPatientsReturnsPageWithRoles() throws Exception {
        doctorKeyCache.put(sessionId, doctorKeyPair.getPrivate());
        Patient patient = encryptedPatient(Map.of("name", "Baby A")).patient();
        when(patientRepository.findPatientsByDoctorId(eq(doctorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(patient), PageRequest.of(0, 20), 1));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.editor));

        PageResponse<PatientResponse> response = patientService.listPatients(doctorId, sessionId, 0, 20, "createdAt", "desc");

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPatientId()).isEqualTo(patientId);
        assertThat(response.getContent().get(0).getAccessRole()).isEqualTo(PatientAccess.AccessRole.editor);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
        assertThat(response.getPagination().getPage()).isZero();
    }

    @Test
    void listPatientsDefaultsSortAndAscendingOrder() throws Exception {
        doctorKeyCache.put(sessionId, doctorKeyPair.getPrivate());
        when(patientRepository.findPatientsByDoctorId(eq(doctorId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<PatientResponse> response = patientService.listPatients(doctorId, sessionId, 0, 10, null, "asc");

        assertThat(response.getContent()).isEmpty();
    }

    // ---------- updatePatient ----------

    @Test
    void updatePatientReencryptsWithExistingDek() throws Exception {
        EncryptedPatient ep = encryptedPatient(Map.of("name", "Baby A", "weight", 3));
        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setPatientData(Map.of("name", "Baby A", "weight", 4));

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(ep.patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.editor));
        when(patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId))
                .thenReturn(Optional.of(ep.key()));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        doctorKeyCache.put(sessionId, doctorKeyPair.getPrivate());

        PatientResponse response = patientService.updatePatient(patientId, request, doctorId, sessionId);

        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getAccessRole()).isEqualTo(PatientAccess.AccessRole.editor);

        // The new payload must decrypt (with the same DEK) to the updated data
        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        Patient saved = captor.getValue();
        SecretKey dek = encryptionService.unwrapKey(
                new EncryptionService.EncryptedData(ep.key().getDekEnc(), ep.key().getDekIv(), ep.key().getDekTag()),
                doctorKeyPair.getPrivate());
        Map<String, Object> decrypted = encryptionService.decryptJson(
                new EncryptionService.EncryptedData(saved.getEncPayload(), saved.getEncPayloadIv(),
                        saved.getEncPayloadTag()),
                dek);
        assertThat(decrypted).containsEntry("weight", 4);

        verify(auditService).logAction(eq("update"), eq("patient"), eq(patientId), eq(doctorId), any(), any());
    }

    @Test
    void updatePatientForbiddenForViewer() throws Exception {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(encryptedPatient(Map.of()).patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.viewer));

        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setPatientData(Map.of("name", "Changed"));

        assertThatThrownBy(() -> patientService.updatePatient(patientId, request, doctorId, sessionId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner or editor");
    }

    @Test
    void updatePatientUnauthorizedWithoutSessionKey() throws Exception {
        EncryptedPatient ep = encryptedPatient(Map.of("name", "Baby A"));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(ep.patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
        when(patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId))
                .thenReturn(Optional.of(ep.key()));

        PatientUpdateRequest request = new PatientUpdateRequest();
        request.setPatientData(Map.of("name", "Changed"));

        assertThatThrownBy(() -> patientService.updatePatient(patientId, request, doctorId, sessionId))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---------- deletePatient ----------

    @Test
    void deletePatientAllowedForOwner() throws Exception {
        Patient patient = encryptedPatient(Map.of()).patient();
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));

        patientService.deletePatient(patientId, doctorId);

        verify(patientRepository).delete(patient);
        verify(auditService).logAction(eq("delete"), eq("patient"), eq(patientId), eq(doctorId), any(), any());
    }

    @Test
    void deletePatientForbiddenForEditor() throws Exception {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(encryptedPatient(Map.of()).patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.editor));

        assertThatThrownBy(() -> patientService.deletePatient(patientId, doctorId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("owner role");
    }

    @Test
    void deletePatientForbiddenWithoutAccess() throws Exception {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(encryptedPatient(Map.of()).patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.deletePatient(patientId, doctorId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletePatientThrowsWhenMissing() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.deletePatient(patientId, doctorId))
                .isInstanceOf(NotFoundException.class);
    }
}
