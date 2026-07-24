package com.ecgcare.backend.controller;

import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.Patient;
import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.exception.GlobalExceptionHandler;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.PatientAccessRepository;
import com.ecgcare.backend.repository.PatientRepository;
import com.ecgcare.backend.service.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PatientAccessControllerTest {

    @Mock
    private PatientAccessRepository patientAccessRepository;
    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private AuditService auditService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID recipientId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(ownerId.toString(), UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PatientAccessController(
                patientAccessRepository, patientRepository, doctorRepository, auditService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void stubRequesterRole(PatientAccess.AccessRole role) {
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, ownerId))
                .thenReturn(Optional.ofNullable(role));
    }

    private Doctor doctor(UUID id) {
        return Doctor.builder().doctorId(id).email(id + "@example.com").fullName("Dr. " + id)
                .isActive(true).build();
    }

    // ---------- share ----------

    @Test
    void ownerCanShareAccess() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.owner);
        when(doctorRepository.existsById(recipientId)).thenReturn(true);
        when(patientAccessRepository.existsByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientId))
                .thenReturn(false);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(
                Patient.builder().patientId(patientId).encPayload(new byte[1])
                        .encPayloadIv(new byte[1]).encPayloadTag(new byte[1]).build()));
        when(doctorRepository.findById(recipientId)).thenReturn(Optional.of(doctor(recipientId)));
        when(doctorRepository.findById(ownerId)).thenReturn(Optional.of(doctor(ownerId)));

        mockMvc.perform(post("/api/patients/{patientId}/access/share", patientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "recipientDoctorId", recipientId.toString(),
                        "role", "viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Access granted successfully"));

        ArgumentCaptor<PatientAccess> captor = ArgumentCaptor.forClass(PatientAccess.class);
        verify(patientAccessRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(PatientAccess.AccessRole.viewer);
        assertThat(captor.getValue().getDoctor().getDoctorId()).isEqualTo(recipientId);
        assertThat(captor.getValue().getGrantedBy().getDoctorId()).isEqualTo(ownerId);
    }

    @Test
    void nonOwnerCannotShare() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.editor);

        mockMvc.perform(post("/api/patients/{patientId}/access/share", patientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "recipientDoctorId", recipientId.toString(),
                        "role", "viewer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("Only owner can share access"));

        verify(patientAccessRepository, never()).save(any());
    }

    @Test
    void shareWithUnknownRecipientReturns404() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.owner);
        when(doctorRepository.existsById(recipientId)).thenReturn(false);

        mockMvc.perform(post("/api/patients/{patientId}/access/share", patientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "recipientDoctorId", recipientId.toString(),
                        "role", "editor"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("Recipient doctor not found"));
    }

    @Test
    void shareTwiceReturns403() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.owner);
        when(doctorRepository.existsById(recipientId)).thenReturn(true);
        when(patientAccessRepository.existsByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientId))
                .thenReturn(true);

        mockMvc.perform(post("/api/patients/{patientId}/access/share", patientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "recipientDoctorId", recipientId.toString(),
                        "role", "viewer"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("Access already granted"));
    }

    @Test
    void shareWithoutAccessAtAllReturns403() throws Exception {
        stubRequesterRole(null);

        mockMvc.perform(post("/api/patients/{patientId}/access/share", patientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "recipientDoctorId", recipientId.toString(),
                        "role", "viewer"))))
                .andExpect(status().isForbidden());
    }

    // ---------- update role ----------

    @Test
    void ownerCanUpdateAccessRole() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.owner);
        PatientAccess access = PatientAccess.builder()
                .doctor(doctor(recipientId)).role(PatientAccess.AccessRole.viewer).build();
        when(patientAccessRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientId))
                .thenReturn(Optional.of(access));

        mockMvc.perform(put("/api/patients/{patientId}/access/{recipientId}", patientId, recipientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("role", "editor"))))
                .andExpect(status().isOk());

        assertThat(access.getRole()).isEqualTo(PatientAccess.AccessRole.editor);
        verify(patientAccessRepository).save(access);
    }

    @Test
    void updateRoleForMissingAccessReturns404() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.owner);
        when(patientAccessRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientId))
                .thenReturn(Optional.empty());

        mockMvc.perform(put("/api/patients/{patientId}/access/{recipientId}", patientId, recipientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("role", "editor"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonOwnerCannotUpdateRole() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.viewer);

        mockMvc.perform(put("/api/patients/{patientId}/access/{recipientId}", patientId, recipientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("role", "editor"))))
                .andExpect(status().isForbidden());
    }

    // ---------- revoke ----------

    @Test
    void ownerCanRevokeAccess() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.owner);
        PatientAccess access = PatientAccess.builder().doctor(doctor(recipientId))
                .role(PatientAccess.AccessRole.viewer).build();
        when(patientAccessRepository.findByPatient_PatientIdAndDoctor_DoctorId(patientId, recipientId))
                .thenReturn(Optional.of(access));

        mockMvc.perform(delete("/api/patients/{patientId}/access/{recipientId}", patientId, recipientId)
                .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Access revoked successfully"));

        verify(patientAccessRepository).delete(access);
    }

    @Test
    void nonOwnerCannotRevoke() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.editor);

        mockMvc.perform(delete("/api/patients/{patientId}/access/{recipientId}", patientId, recipientId)
                .principal(auth()))
                .andExpect(status().isForbidden());

        verify(patientAccessRepository, never()).delete(any());
    }

    // ---------- list ----------

    @Test
    void listAccessReturnsAllGrants() throws Exception {
        stubRequesterRole(PatientAccess.AccessRole.viewer);
        PatientAccess access = PatientAccess.builder()
                .doctor(doctor(recipientId))
                .grantedBy(doctor(ownerId))
                .role(PatientAccess.AccessRole.editor)
                .build();
        when(patientAccessRepository.findByPatientId(patientId)).thenReturn(List.of(access));

        mockMvc.perform(get("/api/patients/{patientId}/access", patientId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].doctorId").value(recipientId.toString()))
                .andExpect(jsonPath("$.data[0].role").value("editor"))
                .andExpect(jsonPath("$.data[0].grantedBy").value(ownerId.toString()));
    }

    @Test
    void listAccessForbiddenWithoutAccess() throws Exception {
        stubRequesterRole(null);

        mockMvc.perform(get("/api/patients/{patientId}/access", patientId).principal(auth()))
                .andExpect(status().isForbidden());
    }
}
