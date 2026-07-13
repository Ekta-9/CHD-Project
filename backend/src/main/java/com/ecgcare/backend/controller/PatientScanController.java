package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.ScanResponse;
import com.ecgcare.backend.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients/{patientId}/scans")
@RequiredArgsConstructor
public class PatientScanController {
    private final ScanService scanService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ScanResponse>>> listPatientScans(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        PageResponse<ScanResponse> response = scanService.listPatientScans(patientId, doctorId, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}







