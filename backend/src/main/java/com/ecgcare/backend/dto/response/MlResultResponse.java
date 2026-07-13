package com.ecgcare.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlResultResponse {
    private UUID resultId;
    private UUID scanId;
    private UUID patientId;
    private String modelVersion;
    private String predictedLabel;
    private BigDecimal confidenceScore;
    private Map<String, Object> classProbabilities;
    private BigDecimal threshold;
    private String explanationUri;
    private UUID createdBy;
    private OffsetDateTime createdAt;
}









