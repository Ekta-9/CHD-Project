package com.ecgcare.backend.controller;

import com.ecgcare.backend.dto.response.ApiResponse;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.ScanResponse;
import com.ecgcare.backend.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/scans")
@RequiredArgsConstructor
public class ScanController {
    private final ScanService scanService;

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ScanResponse>> uploadScan(
            @RequestParam("file") MultipartFile file,
            @RequestParam("patientId") UUID patientId,
            @RequestParam(value = "metadata", required = false) String metadata,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());

        Map<String, Object> metadataMap = new HashMap<>();
        if (metadata != null && !metadata.isEmpty()) {
            // Parse metadata if provided as JSON string
            metadataMap.put("notes", metadata);
        }

        ScanResponse response = scanService.uploadScan(file, patientId, doctorId, metadataMap);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Scan uploaded successfully", response));
    }

    @GetMapping("/{scanId}")
    public ResponseEntity<ApiResponse<ScanResponse>> getScan(
            @PathVariable UUID scanId,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        ScanResponse response = scanService.getScan(scanId, doctorId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{scanId}/download")
    public ResponseEntity<InputStreamResource> downloadScan(
            @PathVariable UUID scanId,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        InputStream inputStream = scanService.downloadScan(scanId, doctorId);

        ScanResponse scanInfo = scanService.getScan(scanId, doctorId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"scan." +
                        scanInfo.getMimetype().split("/")[1] + "\"")
                .contentType(MediaType.parseMediaType(scanInfo.getMimetype()))
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{scanId}")
    public ResponseEntity<ApiResponse<?>> deleteScan(
            @PathVariable UUID scanId,
            Authentication authentication) {
        UUID doctorId = UUID.fromString(authentication.getName());
        scanService.deleteScan(scanId, doctorId);
        return ResponseEntity.ok(ApiResponse.success("Scan deleted successfully", null));
    }
}







