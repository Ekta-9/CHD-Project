package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.MlResultResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.service.MLService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients/{patientId}/predictions")
@RequiredArgsConstructor
public class PatientPredictionController {
    private final MLService mlService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MlResultResponse>>> listPatientPredictions(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        PageResponse<MlResultResponse> response = mlService.listPatientPredictions(patientId, doctorId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}







