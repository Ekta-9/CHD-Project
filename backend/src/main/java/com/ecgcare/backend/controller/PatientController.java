package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.request.PatientCreateRequest;
import com.ecgcare.backend.dto.request.PatientUpdateRequest;
import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.PatientResponse;
import com.ecgcare.backend.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {
    private final PatientService patientService;

    @PostMapping
    public ResponseEntity<ApiResponse<PatientResponse>> createPatient(
            @Valid @RequestBody PatientCreateRequest request,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        PatientResponse response = patientService.createPatient(request, doctorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Patient created successfully", response));
    }

    @GetMapping("/{patientId}")
    public ResponseEntity<ApiResponse<PatientResponse>> getPatient(
            @PathVariable UUID patientId,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        PatientResponse response = patientService.getPatient(patientId, doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PatientResponse>>> listPatients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        PageResponse<PatientResponse> response = patientService.listPatients(doctorId, page, size, sort, order);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{patientId}")
    public ResponseEntity<ApiResponse<PatientResponse>> updatePatient(
            @PathVariable UUID patientId,
            @Valid @RequestBody PatientUpdateRequest request,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        PatientResponse response = patientService.updatePatient(patientId, request, doctorId);
        return ResponseEntity.ok(ApiResponse.success("Patient updated successfully", response));
    }

    @DeleteMapping("/{patientId}")
    public ResponseEntity<ApiResponse<?>> deletePatient(
            @PathVariable UUID patientId,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        patientService.deletePatient(patientId, doctorId);
        return ResponseEntity.ok(ApiResponse.success("Patient deleted successfully", null));
    }
}









