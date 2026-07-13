package com.ecgcare.backend.dto.response;

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
public class DoctorResponse {
    private UUID doctorId;
    private String email;
    private String fullName;
    private String phone;
    private Boolean isActive;
    private Boolean mfaEnabled;
    private OffsetDateTime createdAt;
}






