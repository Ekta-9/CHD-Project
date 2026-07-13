package com.ecgcare.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class PatientCreateRequest {
    @NotNull(message = "Patient data is required")
    @Valid
    private Map<String, Object> patientData;
}


