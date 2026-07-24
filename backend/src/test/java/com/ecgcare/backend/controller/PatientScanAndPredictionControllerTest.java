package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.MlResultResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.ScanResponse;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.GlobalExceptionHandler;
import com.ecgcare.backend.service.MLService;
import com.ecgcare.backend.service.ScanService;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PatientScanAndPredictionControllerTest {

    @Mock
    private ScanService scanService;
    @Mock
    private MLService mlService;

    private MockMvc mockMvc;
    private final UUID doctorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(doctorId.toString(), UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PatientScanController(scanService),
                new PatientPredictionController(mlService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listPatientScansReturnsPage() throws Exception {
        when(scanService.listPatientScans(patientId, doctorId, 0, 20)).thenReturn(
                PageResponse.<ScanResponse>builder()
                        .content(List.of(ScanResponse.builder().scanId(UUID.randomUUID())
                                .mimetype("image/png").build()))
                        .pagination(PageResponse.PaginationInfo.builder()
                                .page(0).size(20).totalElements(1L).totalPages(1).build())
                        .build());

        mockMvc.perform(get("/api/patients/{patientId}/scans", patientId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].mimetype").value("image/png"));
    }

    @Test
    void listPatientScansForbiddenMapsTo403() throws Exception {
        when(scanService.listPatientScans(patientId, doctorId, 0, 20))
                .thenThrow(new ForbiddenException("No access to this patient"));

        mockMvc.perform(get("/api/patients/{patientId}/scans", patientId).principal(auth()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPatientPredictionsReturnsPage() throws Exception {
        when(mlService.listPatientPredictions(patientId, doctorId, 1, 5)).thenReturn(
                PageResponse.<MlResultResponse>builder()
                        .content(List.of(MlResultResponse.builder()
                                .resultId(UUID.randomUUID()).predictedLabel("Normal").build()))
                        .pagination(PageResponse.PaginationInfo.builder()
                                .page(1).size(5).totalElements(6L).totalPages(2).build())
                        .build());

        mockMvc.perform(get("/api/patients/{patientId}/predictions", patientId)
                .param("page", "1").param("size", "5")
                .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].predictedLabel").value("Normal"))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(2));
    }
}
