package com.ecgcare.backend.dto.response;

import com.ecgcare.backend.entity.PatientAccess.AccessRole;
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
public class PatientResponse {
    private UUID patientId;
    private String anonymizedCode;
    private Map<String, Object> patientData;
    private AccessRole accessRole;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}









