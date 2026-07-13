package com.ecgcare.backend.dto.request;

import com.ecgcare.backend.entity.PatientAccess.AccessRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAccessRoleRequest {
    @NotNull(message = "Role is required")
    private AccessRole role;
}


