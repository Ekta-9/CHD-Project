package com.ecgcare.backend.service;

import com.ecgcare.backend.config.MLProperties;
import com.ecgcare.backend.dto.response.MlResultResponse;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.EcgScan;
import com.ecgcare.backend.entity.MlResult;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.MLServiceException;
import com.ecgcare.backend.exception.MLServiceTimeoutException;
import com.ecgcare.backend.exception.MLServiceUnavailableException;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.EcgScanRepository;
import com.ecgcare.backend.repository.MlResultRepository;
import com.ecgcare.backend.repository.PatientAccessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MLService {
    private final MlResultRepository mlResultRepository;
    private final EcgScanRepository scanRepository;
    private final DoctorRepository doctorRepository;
    private final PatientAccessRepository patientAccessRepository;
    private final AuditService auditService;
    private final RestTemplate restTemplate;
        private final ScanService scanService;
        private final MLProperties mlProperties;

    @Transactional
    public MlResultResponse predict(UUID scanId, UUID doctorId, String modelVersion, BigDecimal threshold) {
        EcgScan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException("Scan not found"));

        // Check access
        patientAccessRepository.findRoleByPatientIdAndDoctorId(scan.getPatient().getPatientId(), doctorId)
                .orElseThrow(() -> new ForbiddenException("No access to this scan"));

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        try {
                        // Download image from MinIO
                        InputStream imageStream = scanService.downloadScan(scanId, doctorId);

                        // Convert image to base64
                        byte[] imageBytes = imageStream.readAllBytes();

                        // Validate image size
                        if (imageBytes.length > mlProperties.getMaxImageSizeBytes()) {
                                throw new MLServiceException(
                                                String.format("Image size (%d bytes) exceeds maximum allowed size (%d bytes)",
                                                                imageBytes.length,
                                                                mlProperties.getMaxImageSizeBytes()));
                        }

                        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                        log.debug("Downloaded image for scan {} (size: {} bytes, base64: {} chars)", scanId,
                                        imageBytes.length, base64Image.length());

                        // Prepare request to ML service
            Map<String, Object> request = new HashMap<>();
                        request.put("scan_id", scanId.toString());
                        request.put("image_data", base64Image);

                        // Set headers for JSON request
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);

                        // Call ML service with retry logic
                        log.info("Calling ML service for scan {}", scanId);
                        Map<String, Object> response = callMLServiceWithRetry(httpEntity);

            if (response == null) {
                                throw new MLServiceException("ML service returned null response");
            }

                        // Parse response
            String predictedLabel = (String) response.get("prediction");
                        if (predictedLabel == null) {
                                throw new MLServiceException("ML service response missing 'prediction' field");
                        }

                        Object confidenceObj = response.get("confidence_score");
                        if (confidenceObj == null) {
                                throw new MLServiceException("ML service response missing 'confidence_score' field");
                        }

                        Double confidenceScore;
                        try {
                                if (confidenceObj instanceof Number) {
                                        confidenceScore = ((Number) confidenceObj).doubleValue();
                                } else {
                                        confidenceScore = Double.parseDouble(confidenceObj.toString());
                                }
                        } catch (NumberFormatException e) {
                                throw new MLServiceException("Invalid confidence_score format in ML service response",
                                                e);
                        }

                        // Validate prediction label (now supports Normal, ASD, VSD)
                        if (!predictedLabel.equals("Normal") && !predictedLabel.equals("ASD") 
                                        && !predictedLabel.equals("VSD")) {
                                log.warn("Unexpected prediction label: {}, expected Normal, ASD, or VSD", predictedLabel);
                        }

            // Get class probabilities from ML service response if available
            Map<String, Object> classProbs = new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, Object> mlClassProbs = (Map<String, Object>) response.get("class_probabilities");
            
            if (mlClassProbs != null && !mlClassProbs.isEmpty()) {
                    // Use probabilities from ML service (all classes)
                    classProbs.putAll(mlClassProbs);
                    log.debug("Using class probabilities from ML service: {}", classProbs);
            } else {
                    // Fallback: create probabilities based on predicted label and confidence
                    // This is a simplified approach - ideally ML service should provide all probabilities
                    classProbs.put("Normal", predictedLabel.equals("Normal") ? confidenceScore : 0.0);
                    classProbs.put("ASD", predictedLabel.equals("ASD") ? confidenceScore : 0.0);
                    classProbs.put("VSD", predictedLabel.equals("VSD") ? confidenceScore : 0.0);
                    log.debug("Using fallback class probabilities for label: {}", predictedLabel);
            }

                        log.info("ML prediction completed for scan {}: {} (confidence: {})", scanId, predictedLabel,
                                        confidenceScore);

            // Save result
            MlResult result = MlResult.builder()
                    .patient(scan.getPatient())
                    .scan(scan)
                    .modelVersion(modelVersion != null ? modelVersion : "v1.0")
                    .predictedLabel(predictedLabel)
                    .classProbs(classProbs)
                    .threshold(threshold != null ? threshold : new BigDecimal("0.5"))
                    .createdBy(doctor)
                    .build();
            result = mlResultRepository.save(result);

            auditService.logAction("predict", "ml_result", result.getResultId(), doctorId, null, null);

            return MlResultResponse.builder()
                    .resultId(result.getResultId())
                    .scanId(scanId)
                    .patientId(scan.getPatient().getPatientId())
                    .modelVersion(result.getModelVersion())
                    .predictedLabel(predictedLabel)
                    .confidenceScore(BigDecimal.valueOf(confidenceScore))
                    .classProbabilities(classProbs)
                    .threshold(result.getThreshold())
                    .createdBy(doctorId)
                    .createdAt(result.getCreatedAt())
                    .build();
                } catch (MLServiceException e) {
                        // Re-throw ML service exceptions as-is
                        log.error("ML service error for scan {}: {}", scanId, e.getMessage());
                        throw e;
        } catch (Exception e) {
                        log.error("Unexpected error during prediction for scan {}", scanId, e);
                        throw new MLServiceException("Failed to get prediction: " + e.getMessage(), e);
                }
        }

        /**
         * Call ML service with retry logic for transient failures
         */
        private Map<String, Object> callMLServiceWithRetry(HttpEntity<Map<String, Object>> httpEntity) {
                String mlServiceUrl = mlProperties.getPredictUrl();
                int maxRetries = mlProperties.getMaxRetries();
                int retryDelay = mlProperties.getRetryDelaySeconds();

                for (int attempt = 1; attempt <= maxRetries; attempt++) {
                        try {
                                log.debug("ML service call attempt {}/{} to {}", attempt, maxRetries, mlServiceUrl);
                                Map<String, Object> response = restTemplate.postForObject(mlServiceUrl, httpEntity,
                                                Map.class);
                                log.debug("ML service call successful on attempt {}", attempt);
                                return response;
                        } catch (RestClientResponseException e) {
                                // Server responded with error status
                                HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
                                if (status != null && (status.is5xxServerError())) {
                                        // Retry on 5xx errors
                                        if (attempt < maxRetries) {
                                                log.warn("ML service returned {} error, retrying ({}/{})",
                                                                status.value(),
                                                                attempt, maxRetries);
                                                sleepWithBackoff(attempt, retryDelay);
                                                continue;
                                        } else {
                                                throw new MLServiceUnavailableException(
                                                                String.format("ML service unavailable after %d attempts: %s",
                                                                                maxRetries, e.getMessage()),
                                                                e);
                                        }
                                } else {
                                        // Don't retry on 4xx errors (client errors)
                                        throw new MLServiceException(
                                                        String.format("ML service returned error: %d %s",
                                                                        e.getStatusCode().value(), e.getMessage()),
                                                        e);
                                }
                        } catch (RestClientException e) {
                                // Network/timeout errors
                                Throwable cause = e.getCause();
                                if (cause instanceof SocketTimeoutException) {
                                        // Timeout - retry
                                        if (attempt < maxRetries) {
                                                log.warn("ML service timeout, retrying ({}/{})", attempt, maxRetries);
                                                sleepWithBackoff(attempt, retryDelay);
                                                continue;
                                        } else {
                                                throw new MLServiceTimeoutException(
                                                                String.format("ML service timeout after %d attempts",
                                                                                maxRetries),
                                                                e);
                                        }
                                } else {
                                        // Connection errors - retry
                                        if (attempt < maxRetries) {
                                                log.warn("ML service connection error: {}, retrying ({}/{})",
                                                                e.getMessage(), attempt, maxRetries);
                                                sleepWithBackoff(attempt, retryDelay);
                                                continue;
                                        } else {
                                                throw new MLServiceUnavailableException(
                                                                String.format("ML service unavailable after %d attempts: %s",
                                                                                maxRetries, e.getMessage()),
                                                                e);
                                        }
                                }
                        }
                }

                throw new MLServiceUnavailableException("ML service unavailable after " + maxRetries + " attempts");
        }

        /**
         * Sleep with exponential backoff between retries
         */
        private void sleepWithBackoff(int attempt, int baseDelaySeconds) {
                try {
                        // Exponential backoff: delay = baseDelay * 2^(attempt-1)
                        long delayMs = baseDelaySeconds * 1000L * (1L << (attempt - 1));
                        log.debug("Waiting {} ms before retry", delayMs);
                        TimeUnit.MILLISECONDS.sleep(delayMs);
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new MLServiceException("Interrupted during retry delay", e);
        }
    }

    public MlResultResponse getResult(UUID resultId, UUID doctorId) {
        MlResult result = mlResultRepository.findById(resultId)
                .orElseThrow(() -> new NotFoundException("Result not found"));

        // Check access
        patientAccessRepository.findRoleByPatientIdAndDoctorId(result.getPatient().getPatientId(), doctorId)
                .orElseThrow(() -> new ForbiddenException("No access to this result"));

        return MlResultResponse.builder()
                .resultId(result.getResultId())
                .scanId(result.getScan() != null ? result.getScan().getScanId() : null)
                .patientId(result.getPatient().getPatientId())
                .modelVersion(result.getModelVersion())
                .predictedLabel(result.getPredictedLabel())
                .confidenceScore(BigDecimal
                                                .valueOf(((Number) result.getClassProbs()
                                                                .get(result.getPredictedLabel())).doubleValue()))
                .classProbabilities(result.getClassProbs())
                .threshold(result.getThreshold())
                .explanationUri(result.getExplanationUri())
                .createdBy(result.getCreatedBy() != null ? result.getCreatedBy().getDoctorId() : null)
                .createdAt(result.getCreatedAt())
                .build();
    }

    public com.ecgcare.backend.dto.response.PageResponse<MlResultResponse> listPatientPredictions(UUID patientId,
            UUID doctorId, int page, int size) {
        // Check access
        patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

        Pageable pageable = PageRequest.of(page, size);
        Page<MlResult> results = mlResultRepository.findByPatientId(patientId, pageable);

        List<MlResultResponse> resultResponses = results.getContent().stream()
                .map(result -> MlResultResponse.builder()
                        .resultId(result.getResultId())
                        .scanId(result.getScan() != null ? result.getScan().getScanId() : null)
                        .predictedLabel(result.getPredictedLabel())
                        .confidenceScore(BigDecimal.valueOf(
                                                                ((Number) result.getClassProbs()
                                                                                .get(result.getPredictedLabel()))
                                                                                .doubleValue()))
                        .createdAt(result.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        com.ecgcare.backend.dto.response.PageResponse.PaginationInfo pagination = com.ecgcare.backend.dto.response.PageResponse.PaginationInfo
                .builder()
                .page(results.getNumber())
                .size(results.getSize())
                .totalElements(results.getTotalElements())
                .totalPages(results.getTotalPages())
                .build();

        return com.ecgcare.backend.dto.response.PageResponse.<MlResultResponse>builder()
                .content(resultResponses)
                .pagination(pagination)
                .build();
    }
}
