package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.ScanResponse;
import com.ecgcare.backend.exception.GlobalExceptionHandler;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.service.ScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ScanControllerTest {

    @Mock
    private ScanService scanService;

    private MockMvc mockMvc;
    private final UUID doctorId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID scanId = UUID.randomUUID();

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(doctorId.toString(), sessionId);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ScanController(scanService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadScanReturns201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png",
                new byte[] { 1, 2, 3 });
        when(scanService.uploadScan(any(), eq(patientId), eq(doctorId), any())).thenReturn(
                ScanResponse.builder().scanId(scanId).patientId(patientId).mimetype("image/png").build());

        mockMvc.perform(multipart("/api/scans/upload")
                .file(file)
                .param("patientId", patientId.toString())
                .principal(auth()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scanId").value(scanId.toString()));
    }

    @Test
    void uploadScanPassesMetadataNotes() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png", new byte[] { 1 });
        when(scanService.uploadScan(any(), eq(patientId), eq(doctorId), any())).thenReturn(
                ScanResponse.builder().scanId(scanId).build());

        mockMvc.perform(multipart("/api/scans/upload")
                .file(file)
                .param("patientId", patientId.toString())
                .param("metadata", "baseline scan")
                .principal(auth()))
                .andExpect(status().isCreated());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(scanService).uploadScan(any(), eq(patientId), eq(doctorId), captor.capture());
        assertThat(captor.getValue()).containsEntry("notes", "baseline scan");
    }

    @Test
    void pendingCountReturnsNumber() throws Exception {
        when(scanService.getPendingScanCount(doctorId)).thenReturn(3L);

        mockMvc.perform(get("/api/scans/pending-count").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(3));
    }

    @Test
    void getScanReturnsMetadata() throws Exception {
        when(scanService.getScan(scanId, doctorId)).thenReturn(
                ScanResponse.builder().scanId(scanId).patientId(patientId).mimetype("image/png").build());

        mockMvc.perform(get("/api/scans/{id}", scanId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mimetype").value("image/png"));
    }

    @Test
    void getScanMissingMapsTo404() throws Exception {
        when(scanService.getScan(scanId, doctorId)).thenThrow(new NotFoundException("Scan not found"));

        mockMvc.perform(get("/api/scans/{id}", scanId).principal(auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void downloadScanStreamsFileWithAttachmentHeader() throws Exception {
        byte[] bytes = { 5, 6, 7 };
        when(scanService.downloadScan(scanId, doctorId)).thenReturn(new ByteArrayInputStream(bytes));
        when(scanService.getScan(scanId, doctorId)).thenReturn(
                ScanResponse.builder().scanId(scanId).mimetype("image/png").build());

        mockMvc.perform(get("/api/scans/{id}/download", scanId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"scan.png\""))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void deleteScanReturns200() throws Exception {
        mockMvc.perform(delete("/api/scans/{id}", scanId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Scan deleted successfully"));

        verify(scanService).deleteScan(scanId, doctorId);
    }
}
