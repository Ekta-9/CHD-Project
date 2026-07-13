package com.ecgcare.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanResponse {
    private UUID scanId;
    private UUID patientId;
    private String storageUri;
    private String mimetype;
    private String checksum;
    private Map<String, Object> metadata;
    private UUID uploadedBy;
    private OffsetDateTime uploadedAt;
}









