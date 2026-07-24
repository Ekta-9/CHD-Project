package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.PatientResponse;
import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.GlobalExceptionHandler;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.service.PatientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class PatientControllerTest {

    @Mock
    private PatientService patientService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(doctorId.toString(), sessionId);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PatientController(patientService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createPatientReturns201() throws Exception {
        when(patientService.createPatient(any(), eq(doctorId))).thenReturn(
                PatientResponse.builder().patientId(patientId).anonymizedCode("PAT-1")
                        .accessRole(PatientAccess.AccessRole.owner).build());

        mockMvc.perform(post("/api/patients")
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("patientData", Map.of("name", "Baby A")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.anonymizedCode").value("PAT-1"))
                .andExpect(jsonPath("$.data.accessRole").value("owner"));
    }

    @Test
    void createPatientWithoutDataFailsValidation() throws Exception {
        mockMvc.perform(post("/api/patients")
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getPatientReturnsDecryptedData() throws Exception {
        when(patientService.getPatient(patientId, doctorId, sessionId)).thenReturn(
                PatientResponse.builder().patientId(patientId)
                        .patientData(Map.of("name", "Baby A")).build());

        mockMvc.perform(get("/api/patients/{id}", patientId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.patientData.name").value("Baby A"));
    }

    @Test
    void getPatientMissingMapsTo404() throws Exception {
        when(patientService.getPatient(patientId, doctorId, sessionId))
                .thenThrow(new NotFoundException("Patient not found"));

        mockMvc.perform(get("/api/patients/{id}", patientId).principal(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getPatientWithoutAccessMapsTo403() throws Exception {
        when(patientService.getPatient(patientId, doctorId, sessionId))
                .thenThrow(new ForbiddenException("No access to this patient"));

        mockMvc.perform(get("/api/patients/{id}", patientId).principal(auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void listPatientsPassesPagingParameters() throws Exception {
        when(patientService.listPatients(doctorId, 2, 5, "updatedAt", "asc")).thenReturn(
                PageResponse.<PatientResponse>builder()
                        .content(List.of(PatientResponse.builder().patientId(patientId).build()))
                        .pagination(PageResponse.PaginationInfo.builder()
                                .page(2).size(5).totalElements(11L).totalPages(3).build())
                        .build());

        mockMvc.perform(get("/api/patients")
                .param("page", "2").param("size", "5")
                .param("sort", "updatedAt").param("order", "asc")
                .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(11));
    }

    @Test
    void updatePatientReturnsUpdatedRecord() throws Exception {
        when(patientService.updatePatient(eq(patientId), any(), eq(doctorId), eq(sessionId))).thenReturn(
                PatientResponse.builder().patientId(patientId)
                        .accessRole(PatientAccess.AccessRole.editor).build());

        mockMvc.perform(put("/api/patients/{id}", patientId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("patientData", Map.of("name", "Updated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Patient updated successfully"));
    }

    @Test
    void deletePatientReturns200() throws Exception {
        mockMvc.perform(delete("/api/patients/{id}", patientId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Patient deleted successfully"));

        verify(patientService).deletePatient(patientId, doctorId);
    }
}
