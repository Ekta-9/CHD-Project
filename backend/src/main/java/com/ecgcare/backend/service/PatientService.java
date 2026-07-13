package com.ecgcare.backend.service;

import com.ecgcare.backend.dto.request.PatientCreateRequest;
import com.ecgcare.backend.dto.request.PatientUpdateRequest;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.PatientResponse;
import com.ecgcare.backend.entity.*;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatientService {
    private final PatientRepository patientRepository;
    private final PatientKeyRepository patientKeyRepository;
    private final PatientAccessRepository patientAccessRepository;
    private final DoctorRepository doctorRepository;
    private final com.ecgcare.backend.repository.DoctorCryptoRepository doctorCryptoRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    @Transactional
    public PatientResponse createPatient(PatientCreateRequest request, UUID doctorId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        try {
            // Encrypt patient data
            EncryptionService.EncryptedDataWithKey encrypted = encryptionService.encryptJson(request.getPatientData());

            // Generate anonymized code
            String anonymizedCode = "PAT-" + System.currentTimeMillis() + "-"
                    + UUID.randomUUID().toString().substring(0, 8);

            // Create patient
            Patient patient = Patient.builder()
                    .anonymizedCode(anonymizedCode)
                    .encPayload(encrypted.encryptedData().data())
                    .encPayloadIv(encrypted.encryptedData().iv())
                    .encPayloadTag(encrypted.encryptedData().tag())
                    .build();
            patient = patientRepository.save(patient);

            // Store encrypted DEK (simplified - using doctor's public key)
            DoctorCrypto doctorCrypto = doctorCryptoRepository.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor crypto not found"));

            // Wrap DEK (simplified)
            EncryptionService.EncryptedData wrappedDek = encryptionService.wrapKey(
                    encrypted.key(),
                    doctorCrypto.getPublicKey());

            PatientKey patientKey = PatientKey.builder()
                    .patient(patient)
                    .doctor(doctor)
                    .wrappingScheme("RSA-OAEP")
                    .dekEnc(wrappedDek.data())
                    .dekIv(wrappedDek.iv())
                    .dekTag(wrappedDek.tag())
                    .build();
            patientKeyRepository.save(patientKey);

            // Create access record
            PatientAccess patientAccess = PatientAccess.builder()
                    .patient(patient)
                    .doctor(doctor)
                    .role(PatientAccess.AccessRole.owner)
                    .grantedAt(OffsetDateTime.now())
                    .build();
            patientAccessRepository.save(patientAccess);

            auditService.logAction("create", "patient", patient.getPatientId(), doctorId, null, null);

            return PatientResponse.builder()
                    .patientId(patient.getPatientId())
                    .anonymizedCode(patient.getAnonymizedCode())
                    .accessRole(PatientAccess.AccessRole.owner)
                    .createdAt(patient.getCreatedAt())
                    .updatedAt(patient.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Failed to create patient", e);
            throw new RuntimeException("Failed to create patient: " + e.getMessage());
        }
    }

    public PatientResponse getPatient(UUID patientId, UUID doctorId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found"));

        // Check access
        PatientAccess.AccessRole role = patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

        try {
            // Get encrypted DEK
            PatientKey patientKey = patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId)
                    .orElseThrow(() -> new RuntimeException("Patient key not found"));

            // Unwrap DEK (simplified)
            DoctorCrypto doctorCrypto = doctorCryptoRepository.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor crypto not found"));

            EncryptionService.EncryptedData wrappedDek = new EncryptionService.EncryptedData(
                    patientKey.getDekEnc(),
                    patientKey.getDekIv(),
                    patientKey.getDekTag());

            SecretKey dek = encryptionService.unwrapKey(wrappedDek, doctorCrypto.getPublicKey());

            // Decrypt patient data
            EncryptionService.EncryptedData encryptedData = new EncryptionService.EncryptedData(
                    patient.getEncPayload(),
                    patient.getEncPayloadIv(),
                    patient.getEncPayloadTag());

            Map<String, Object> patientData = encryptionService.decryptJson(encryptedData, dek);

            auditService.logAction("read", "patient", patientId, doctorId, null, null);

            return PatientResponse.builder()
                    .patientId(patient.getPatientId())
                    .anonymizedCode(patient.getAnonymizedCode())
                    .patientData(patientData)
                    .accessRole(role)
                    .createdAt(patient.getCreatedAt())
                    .updatedAt(patient.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Failed to get patient", e);
            throw new RuntimeException("Failed to get patient: " + e.getMessage());
        }
    }

    public PageResponse<PatientResponse> listPatients(UUID doctorId, int page, int size, String sort, String order) {
        Sort.Direction direction = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort != null ? sort : "createdAt"));

        Page<Patient> patients = patientRepository.findPatientsByDoctorId(doctorId, pageable);

        List<PatientResponse> patientResponses = patients.getContent().stream()
                .map(patient -> {
                    PatientAccess.AccessRole role = patientAccessRepository
                            .findRoleByPatientIdAndDoctorId(patient.getPatientId(), doctorId)
                            .orElse(PatientAccess.AccessRole.viewer);

                    return PatientResponse.builder()
                            .patientId(patient.getPatientId())
                            .anonymizedCode(patient.getAnonymizedCode())
                            .accessRole(role)
                            .createdAt(patient.getCreatedAt())
                            .updatedAt(patient.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        PageResponse.PaginationInfo pagination = PageResponse.PaginationInfo.builder()
                .page(patients.getNumber())
                .size(patients.getSize())
                .totalElements(patients.getTotalElements())
                .totalPages(patients.getTotalPages())
                .build();

        return PageResponse.<PatientResponse>builder()
                .content(patientResponses)
                .pagination(pagination)
                .build();
    }

    @Transactional
    public PatientResponse updatePatient(UUID patientId, PatientUpdateRequest request, UUID doctorId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found"));

        // Check access - must be owner or editor
        PatientAccess.AccessRole role = patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

        if (role != PatientAccess.AccessRole.owner && role != PatientAccess.AccessRole.editor) {
            throw new ForbiddenException("Insufficient permissions. Requires owner or editor role.");
        }

        try {
            // Re-encrypt with existing DEK
            PatientKey patientKey = patientKeyRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, doctorId)
                    .orElseThrow(() -> new RuntimeException("Patient key not found"));

            // Unwrap DEK and encrypt new data
            DoctorCrypto doctorCrypto = doctorCryptoRepository.findById(doctorId)
                    .orElseThrow(() -> new RuntimeException("Doctor crypto not found"));

            EncryptionService.EncryptedData wrappedDek = new EncryptionService.EncryptedData(
                    patientKey.getDekEnc(),
                    patientKey.getDekIv(),
                    patientKey.getDekTag());

            SecretKey dek = encryptionService.unwrapKey(wrappedDek, doctorCrypto.getPublicKey());
            EncryptionService.EncryptedData encrypted = encryptionService.encrypt(
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(request.getPatientData()),
                    dek);

            patient.setEncPayload(encrypted.data());
            patient.setEncPayloadIv(encrypted.iv());
            patient.setEncPayloadTag(encrypted.tag());
            patient = patientRepository.save(patient);

            auditService.logAction("update", "patient", patientId, doctorId, null, null);

            return PatientResponse.builder()
                    .patientId(patient.getPatientId())
                    .anonymizedCode(patient.getAnonymizedCode())
                    .accessRole(role)
                    .updatedAt(patient.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Failed to update patient", e);
            throw new RuntimeException("Failed to update patient: " + e.getMessage());
        }
    }

    @Transactional
    public void deletePatient(UUID patientId, UUID doctorId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new NotFoundException("Patient not found"));

        // Check access - must be owner
        PatientAccess.AccessRole role = patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

        if (role != PatientAccess.AccessRole.owner) {
            throw new ForbiddenException("Insufficient permissions. Requires owner role.");
        }

        patientRepository.delete(patient);
        auditService.logAction("delete", "patient", patientId, doctorId, null, null);
    }
}
