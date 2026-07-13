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
public class DraftResponse {
    private UUID draftId;
    private UUID patientId;
    private String formType;
    private Map<String, Object> formData;
    private OffsetDateTime updatedAt;
}


