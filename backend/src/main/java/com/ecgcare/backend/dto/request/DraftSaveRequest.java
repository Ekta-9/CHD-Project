package com.ecgcare.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class DraftSaveRequest {
    private UUID patientId;

    @NotBlank(message = "Form type is required")
    private String formType;

    @NotNull(message = "Form data is required")
    private Map<String, Object> formData;
}


