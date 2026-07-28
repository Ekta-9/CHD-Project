package com.ecgcare.backend.service;

import com.ecgcare.backend.config.MLProperties;
import com.ecgcare.backend.dto.response.MlResultResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.EcgScan;
import com.ecgcare.backend.entity.MlResult;
import com.ecgcare.backend.entity.Patient;
import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.MLServiceException;
import com.ecgcare.backend.exception.MLServiceTimeoutException;
import com.ecgcare.backend.exception.MLServiceUnavailableException;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.EcgScanRepository;
import com.ecgcare.backend.repository.MlResultRepository;
import com.ecgcare.backend.repository.PatientAccessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MLServiceTest {

    @Mock
    private MlResultRepository mlResultRepository;
    @Mock
    private EcgScanRepository scanRepository;
    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private PatientAccessRepository patientAccessRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ScanService scanService;

    private MLProperties mlProperties;
    private MLService mlService;

    private final UUID doctorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID scanId = UUID.randomUUID();
    private final UUID resultId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mlProperties = new MLProperties();
        mlProperties.setMaxRetries(2);
        mlProperties.setRetryDelaySeconds(0); // no backoff sleep in tests
        mlService = new MLService(mlResultRepository, scanRepository, doctorRepository,
                patientAccessRepository, auditService, restTemplate, scanService, mlProperties);
    }

    private Doctor doctor() {
        return Doctor.builder().doctorId(doctorId).email("doc@example.com")
                .fullName("Dr. Test").isActive(true).build();
    }

    private Patient patient() {
        return Patient.builder().patientId(patientId).encPayload(new byte[1])
                .encPayloadIv(new byte[1]).encPayloadTag(new byte[1]).build();
    }

    private EcgScan scan() {
        return EcgScan.builder().scanId(scanId).patient(patient())
                .storageUri("uri").mimetype("image/png").build();
    }

    private void stubAccessibleScan() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));
        when(scanService.downloadScan(scanId, doctorId))
                .thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));
    }

    private void stubSavedResult() {
        when(mlResultRepository.save(any(MlResult.class))).thenAnswer(inv -> {
            MlResult r = inv.getArgument(0);
            r.setResultId(resultId);
            return r;
        });
    }

    private void stubMlResponse(Map<String, Object> response) {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(response);
    }

    // ---------- predict: success paths ----------

    @Test
    void predictSavesResultWithProbabilitiesFromMlService() {
        stubAccessibleScan();
        stubSavedResult();
        Map<String, Object> mlResponse = new HashMap<>();
        mlResponse.put("prediction", "ASD");
        mlResponse.put("confidence_score", 0.93);
        mlResponse.put("class_probabilities", Map.of("Normal", 0.05, "ASD", 0.93, "VSD", 0.02));
        stubMlResponse(mlResponse);

        MlResultResponse response = mlService.predict(scanId, doctorId, "v2.0", new BigDecimal("0.6"));

        assertThat(response.getResultId()).isEqualTo(resultId);
        assertThat(response.getPredictedLabel()).isEqualTo("ASD");
        assertThat(response.getConfidenceScore()).isEqualByComparingTo("0.93");
        assertThat(response.getClassProbabilities()).containsEntry("ASD", 0.93);
        assertThat(response.getModelVersion()).isEqualTo("v2.0");
        assertThat(response.getThreshold()).isEqualByComparingTo("0.6");

        verify(auditService).logAction(eq("predict"), eq("ml_result"), eq(resultId), eq(doctorId), any(), any());
    }

    @Test
    void predictBuildsFallbackProbabilitiesWhenServiceOmitsThem() {
        stubAccessibleScan();
        stubSavedResult();
        Map<String, Object> mlResponse = new HashMap<>();
        mlResponse.put("prediction", "Normal");
        mlResponse.put("confidence_score", "0.88"); // string form also accepted
        stubMlResponse(mlResponse);

        MlResultResponse response = mlService.predict(scanId, doctorId, null, null);

        assertThat(response.getPredictedLabel()).isEqualTo("Normal");
        assertThat(response.getClassProbabilities())
                .containsEntry("Normal", 0.88)
                .containsEntry("ASD", 0.0)
                .containsEntry("VSD", 0.0);
        assertThat(response.getModelVersion()).isEqualTo("v1.0");
        assertThat(response.getThreshold()).isEqualByComparingTo("0.5");
    }

    @Test
    void predictAcceptsUnexpectedLabelWithWarning() {
        stubAccessibleScan();
        stubSavedResult();
        Map<String, Object> mlResponse = new HashMap<>();
        mlResponse.put("prediction", "TOF");
        mlResponse.put("confidence_score", 0.7);
        stubMlResponse(mlResponse);

        MlResultResponse response = mlService.predict(scanId, doctorId, null, null);

        assertThat(response.getPredictedLabel()).isEqualTo("TOF");
    }

    @Test
    void predictRetriesOn5xxThenSucceeds() {
        stubAccessibleScan();
        stubSavedResult();
        Map<String, Object> mlResponse = new HashMap<>();
        mlResponse.put("prediction", "VSD");
        mlResponse.put("confidence_score", 0.8);
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(mlResponse);

        MlResultResponse response = mlService.predict(scanId, doctorId, null, null);

        assertThat(response.getPredictedLabel()).isEqualTo("VSD");
        verify(restTemplate, times(2)).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    // ---------- predict: malformed responses ----------

    @Test
    void predictFailsWhenResponseIsNull() {
        stubAccessibleScan();
        stubMlResponse(null);

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceException.class)
                .hasMessageContaining("null response");
    }

    @Test
    void predictFailsWhenPredictionMissing() {
        stubAccessibleScan();
        stubMlResponse(Map.of("confidence_score", 0.9));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceException.class)
                .hasMessageContaining("prediction");
    }

    @Test
    void predictFailsWhenConfidenceMissing() {
        stubAccessibleScan();
        stubMlResponse(Map.of("prediction", "Normal"));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceException.class)
                .hasMessageContaining("confidence_score");
    }

    @Test
    void predictFailsWhenConfidenceUnparseable() {
        stubAccessibleScan();
        stubMlResponse(Map.of("prediction", "Normal", "confidence_score", "not-a-number"));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceException.class)
                .hasMessageContaining("confidence_score");
    }

    // ---------- predict: transport failures ----------

    @Test
    void predictDoesNotRetryClientErrors() {
        stubAccessibleScan();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceException.class)
                .isNotInstanceOf(MLServiceUnavailableException.class);
        verify(restTemplate, times(1)).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void predictReportsUnavailableAfterExhausted5xxRetries() {
        stubAccessibleScan();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceUnavailableException.class);
        verify(restTemplate, times(2)).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void predictReportsTimeoutAfterExhaustedTimeoutRetries() {
        stubAccessibleScan();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceTimeoutException.class);
        verify(restTemplate, times(2)).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void predictReportsUnavailableAfterConnectionErrors() {
        stubAccessibleScan();
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("refused", new ConnectException("connection refused")));

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceUnavailableException.class);
        verify(restTemplate, times(2)).postForObject(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    // ---------- predict: guards ----------

    @Test
    void predictRejectsOversizedImage() {
        mlProperties.setMaxImageSizeBytes(2); // image stub is 3 bytes
        stubAccessibleScan();

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(MLServiceException.class)
                .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    void predictFailsWhenScanMissing() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void predictForbiddenWithoutAccess() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mlService.predict(scanId, doctorId, null, null))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- getResult ----------

    private MlResult storedResult() {
        return MlResult.builder()
                .resultId(resultId)
                .patient(patient())
                .scan(scan())
                .modelVersion("v1.0")
                .predictedLabel("ASD")
                .classProbs(Map.of("Normal", 0.1, "ASD", 0.85, "VSD", 0.05))
                .threshold(new BigDecimal("0.5"))
                .createdBy(doctor())
                .build();
    }

    @Test
    void getResultReturnsStoredPrediction() {
        when(mlResultRepository.findById(resultId)).thenReturn(Optional.of(storedResult()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.viewer));

        MlResultResponse response = mlService.getResult(resultId, doctorId);

        assertThat(response.getResultId()).isEqualTo(resultId);
        assertThat(response.getScanId()).isEqualTo(scanId);
        assertThat(response.getPredictedLabel()).isEqualTo("ASD");
        assertThat(response.getConfidenceScore()).isEqualByComparingTo("0.85");
        assertThat(response.getCreatedBy()).isEqualTo(doctorId);
    }

    @Test
    void getResultThrowsWhenMissing() {
        when(mlResultRepository.findById(resultId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mlService.getResult(resultId, doctorId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getResultForbiddenWithoutAccess() {
        when(mlResultRepository.findById(resultId)).thenReturn(Optional.of(storedResult()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mlService.getResult(resultId, doctorId))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- listPatientPredictions ----------

    @Test
    void listPatientPredictionsReturnsPage() {
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
        when(mlResultRepository.findByPatientId(eq(patientId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(storedResult()), PageRequest.of(0, 20), 1));

        PageResponse<MlResultResponse> response = mlService.listPatientPredictions(patientId, doctorId, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getPredictedLabel()).isEqualTo("ASD");
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
    }

    @Test
    void listPatientPredictionsForbiddenWithoutAccess() {
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> mlService.listPatientPredictions(patientId, doctorId, 0, 20))
                .isInstanceOf(ForbiddenException.class);
    }
}
