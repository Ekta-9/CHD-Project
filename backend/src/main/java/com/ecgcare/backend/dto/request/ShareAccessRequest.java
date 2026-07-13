package com.ecgcare.backend.dto.request;

import com.ecgcare.backend.entity.PatientAccess.AccessRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ShareAccessRequest {
    @NotNull(message = "Recipient doctor ID is required")
    private UUID recipientDoctorId;

    @NotNull(message = "Role is required")
    private AccessRole role;
}


