package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.request.ShareAccessRequest;
import com.ecgcare.backend.dto.request.UpdateAccessRoleRequest;
import com.ecgcare.backend.dto.response.AccessInfoResponse;
import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.PatientAccessRepository;
import com.ecgcare.backend.repository.PatientRepository;
import com.ecgcare.backend.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patients/{patientId}/access")
@RequiredArgsConstructor
public class PatientAccessController {
        private final PatientAccessRepository patientAccessRepository;
        private final PatientRepository patientRepository;
        private final DoctorRepository doctorRepository;
        private final AuditService auditService;

        @PostMapping("/share")
        public ResponseEntity<ApiResponse<?>> shareAccess(
                        @PathVariable UUID patientId,
                        @Valid @RequestBody ShareAccessRequest request,
                        Authentication authentication) {
                UUID doctorId = UUID.fromString(authentication.getName());

                // Check if requester is owner
                PatientAccess.AccessRole requesterRole = patientAccessRepository
                                .findRoleByPatientIdAndDoctorId(patientId, doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

                if (requesterRole != PatientAccess.AccessRole.owner) {
                        throw new ForbiddenException("Only owner can share access");
                }

                // Verify recipient exists
                if (!doctorRepository.existsById(request.getRecipientDoctorId())) {
                        throw new NotFoundException("Recipient doctor not found");
                }

                // Check if access already exists
                if (patientAccessRepository.existsByPatient_PatientIdAndDoctor_DoctorId(patientId,
                                request.getRecipientDoctorId())) {
                        throw new ForbiddenException("Access already granted");
                }

                // Create access (simplified - key wrapping would happen here)
                PatientAccess patientAccess = PatientAccess.builder()
                                .patient(patientRepository.findById(patientId).orElseThrow())
                                .doctor(doctorRepository.findById(request.getRecipientDoctorId()).orElseThrow())
                                .role(request.getRole())
                                .grantedBy(doctorRepository.findById(doctorId).orElseThrow())
                                .grantedAt(OffsetDateTime.now())
                                .build();

                patientAccessRepository.save(patientAccess);
                auditService.logAction("share", "patient_access", patientId, doctorId, null, null);

                return ResponseEntity.ok(ApiResponse.success("Access granted successfully", null));
        }

        @PutMapping("/{recipientDoctorId}")
        public ResponseEntity<ApiResponse<?>> updateAccessRole(
                        @PathVariable UUID patientId,
                        @PathVariable UUID recipientDoctorId,
                        @Valid @RequestBody UpdateAccessRoleRequest request,
                        Authentication authentication) {
                UUID doctorId = UUID.fromString(authentication.getName());

                // Check if requester is owner
                PatientAccess.AccessRole requesterRole = patientAccessRepository
                                .findRoleByPatientIdAndDoctorId(patientId, doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

                if (requesterRole != PatientAccess.AccessRole.owner) {
                        throw new ForbiddenException("Only owner can update access");
                }

                PatientAccess patientAccess = patientAccessRepository
                                .findByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientDoctorId)
                                .orElseThrow(() -> new NotFoundException("Access not found"));

                patientAccess.setRole(request.getRole());
                patientAccessRepository.save(patientAccess);

                return ResponseEntity.ok(ApiResponse.success("Access role updated successfully", null));
        }

        @DeleteMapping("/{recipientDoctorId}")
        public ResponseEntity<ApiResponse<?>> revokeAccess(
                        @PathVariable UUID patientId,
                        @PathVariable UUID recipientDoctorId,
                        Authentication authentication) {
                UUID doctorId = UUID.fromString(authentication.getName());

                // Check if requester is owner
                PatientAccess.AccessRole requesterRole = patientAccessRepository
                                .findRoleByPatientIdAndDoctorId(patientId, doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

                if (requesterRole != PatientAccess.AccessRole.owner) {
                        throw new ForbiddenException("Only owner can revoke access");
                }

                PatientAccess patientAccess = patientAccessRepository
                                .findByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientDoctorId)
                                .orElseThrow(() -> new NotFoundException("Access not found"));

                patientAccessRepository.delete(patientAccess);
                auditService.logAction("revoke", "patient_access", patientId, doctorId, null, null);

                return ResponseEntity.ok(ApiResponse.success("Access revoked successfully", null));
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<AccessInfoResponse>>> listAccess(
                        @PathVariable UUID patientId,
                        Authentication authentication) {
                UUID doctorId = UUID.fromString(authentication.getName());

                // Check access
                patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

                List<PatientAccess> accessList = patientAccessRepository.findByPatientId(patientId);

                List<AccessInfoResponse> responses = accessList.stream()
                                .map(access -> AccessInfoResponse.builder()
                                                .doctorId(access.getDoctor().getDoctorId())
                                                .doctorName(access.getDoctor().getFullName())
                                                .doctorEmail(access.getDoctor().getEmail())
                                                .role(access.getRole())
                                                .grantedBy(access.getGrantedBy() != null
                                                                ? access.getGrantedBy().getDoctorId()
                                                                : null)
                                                .grantedAt(access.getGrantedAt())
                                                .build())
                                .collect(Collectors.toList());

                return ResponseEntity.ok(ApiResponse.success(responses));
        }
}






