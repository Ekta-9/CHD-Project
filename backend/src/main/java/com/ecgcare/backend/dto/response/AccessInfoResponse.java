package com.ecgcare.backend.dto.response;

import com.ecgcare.backend.entity.PatientAccess.AccessRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessInfoResponse {
    private UUID doctorId;
    private String doctorName;
    private String doctorEmail;
    private AccessRole role;
    private UUID grantedBy;
    private OffsetDateTime grantedAt;
}









