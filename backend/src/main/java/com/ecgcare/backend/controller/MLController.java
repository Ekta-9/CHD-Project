package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.request.PredictRequest;
import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.MlResultResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.service.MLService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
public class MLController {
    private final MLService mlService;

    @PostMapping("/predict/{scanId}")
    public ResponseEntity<ApiResponse<MlResultResponse>> predict(
            @PathVariable UUID scanId,
            @RequestBody(required = false) PredictRequest request,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());

        String modelVersion = request != null ? request.getModelVersion() : "v1.0";
        BigDecimal threshold = request != null ? request.getThreshold() : new BigDecimal("0.5");

        MlResultResponse response = mlService.predict(scanId, doctorId, modelVersion, threshold);
        return ResponseEntity.ok(ApiResponse.success("Prediction completed", response));
    }

    @GetMapping("/results/{resultId}")
    public ResponseEntity<ApiResponse<MlResultResponse>> getResult(
            @PathVariable UUID resultId,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        MlResultResponse response = mlService.getResult(resultId, doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}







