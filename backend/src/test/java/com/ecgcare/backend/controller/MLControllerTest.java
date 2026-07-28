package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.MlResultResponse;
import com.ecgcare.backend.exception.GlobalExceptionHandler;
import com.ecgcare.backend.exception.MLServiceException;
import com.ecgcare.backend.exception.MLServiceTimeoutException;
import com.ecgcare.backend.exception.MLServiceUnavailableException;
import com.ecgcare.backend.service.MLService;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MLControllerTest {

    @Mock
    private MLService mlService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID doctorId = UUID.randomUUID();
    private final UUID scanId = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken(doctorId.toString(), UUID.randomUUID());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MLController(mlService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void predictWithoutBodyUsesDefaults() throws Exception {
        when(mlService.predict(scanId, doctorId, "v1.0", new BigDecimal("0.5"))).thenReturn(
                MlResultResponse.builder().resultId(resultId).predictedLabel("Normal").build());

        mockMvc.perform(post("/api/ml/predict/{scanId}", scanId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predictedLabel").value("Normal"))
                .andExpect(jsonPath("$.message").value("Prediction completed"));
    }

    @Test
    void predictWithBodyPassesModelVersionAndThreshold() throws Exception {
        when(mlService.predict(scanId, doctorId, "v2.1", new BigDecimal("0.75"))).thenReturn(
                MlResultResponse.builder().resultId(resultId).predictedLabel("ASD").build());

        mockMvc.perform(post("/api/ml/predict/{scanId}", scanId)
                .principal(auth())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "modelVersion", "v2.1",
                        "threshold", 0.75))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predictedLabel").value("ASD"));
    }

    @Test
    void mlServiceTimeoutMapsTo504() throws Exception {
        when(mlService.predict(eq(scanId), eq(doctorId), eq("v1.0"), eq(new BigDecimal("0.5"))))
                .thenThrow(new MLServiceTimeoutException("timeout after 3 attempts"));

        mockMvc.perform(post("/api/ml/predict/{scanId}", scanId).principal(auth()))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("ML_SERVICE_TIMEOUT"));
    }

    @Test
    void mlServiceUnavailableMapsTo503() throws Exception {
        when(mlService.predict(eq(scanId), eq(doctorId), eq("v1.0"), eq(new BigDecimal("0.5"))))
                .thenThrow(new MLServiceUnavailableException("service down"));

        mockMvc.perform(post("/api/ml/predict/{scanId}", scanId).principal(auth()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("ML_SERVICE_UNAVAILABLE"));
    }

    @Test
    void mlServiceErrorMapsTo502() throws Exception {
        when(mlService.predict(eq(scanId), eq(doctorId), eq("v1.0"), eq(new BigDecimal("0.5"))))
                .thenThrow(new MLServiceException("bad response"));

        mockMvc.perform(post("/api/ml/predict/{scanId}", scanId).principal(auth()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("ML_SERVICE_ERROR"));
    }

    @Test
    void getResultReturnsStoredPrediction() throws Exception {
        when(mlService.getResult(resultId, doctorId)).thenReturn(
                MlResultResponse.builder().resultId(resultId).predictedLabel("VSD")
                        .confidenceScore(new BigDecimal("0.91")).build());

        mockMvc.perform(get("/api/ml/results/{id}", resultId).principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.predictedLabel").value("VSD"))
                .andExpect(jsonPath("$.data.confidenceScore").value(0.91));
    }
}
