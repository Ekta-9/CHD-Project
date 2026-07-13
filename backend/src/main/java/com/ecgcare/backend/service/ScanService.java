package com.ecgcare.backend.service;

import com.ecgcare.backend.dto.response.ScanResponse;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.EcgScan;
import com.ecgcare.backend.entity.Patient;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.NotFoundException;
import com.ecgcare.backend.repository.DoctorRepository;
import com.ecgcare.backend.repository.EcgScanRepository;
import com.ecgcare.backend.repository.PatientAccessRepository;
import com.ecgcare.backend.repository.PatientRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanService {
        private final EcgScanRepository scanRepository;
        private final PatientRepository patientRepository;
        private final DoctorRepository doctorRepository;
        private final PatientAccessRepository patientAccessRepository;
        private final MinioClient minioClient;
        private final com.ecgcare.backend.config.MinIOProperties minIOProperties;
        private final AuditService auditService;

        @Transactional
        public ScanResponse uploadScan(MultipartFile file, UUID patientId, UUID doctorId,
                        Map<String, Object> metadata) {
                Patient patient = patientRepository.findById(patientId)
                                .orElseThrow(() -> new NotFoundException("Patient not found"));

                // Check access
                patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

                Doctor doctor = doctorRepository.findById(doctorId)
                                .orElseThrow(() -> new NotFoundException("Doctor not found"));

                try {
                        // Validate file
                        if (file.isEmpty()) {
                                throw new IllegalArgumentException("File is empty");
                        }

                        String mimetype = file.getContentType();
                        if (mimetype == null || (!mimetype.startsWith("image/"))) {
                                throw new IllegalArgumentException("Invalid file type. Only images are allowed.");
                        }

                        // Generate storage URI
                        UUID scanId = UUID.randomUUID();
                        String storageUri = String.format("%s/%s/%s", patientId, scanId, file.getOriginalFilename());

                        // Upload to MinIO
                        minioClient.putObject(PutObjectArgs.builder()
                                        .bucket(minIOProperties.getBucket())
                                        .object(storageUri)
                                        .stream(file.getInputStream(), file.getSize(), -1)
                                        .contentType(mimetype)
                                        .build());

                        // Calculate checksum (simplified)
                        String checksum = "sha256:" + UUID.randomUUID().toString();

                        // Save metadata
                        EcgScan scan = EcgScan.builder()
                                        .patient(patient)
                                        .storageUri(storageUri)
                                        .mimetype(mimetype)
                                        .uploadedBy(doctor)
                                        .checksum(checksum)
                                        .metadata(metadata != null ? metadata : new HashMap<>())
                                        .build();
                        scan = scanRepository.save(scan);

                        auditService.logAction("upload", "scan", scan.getScanId(), doctorId, null, null);

                        return ScanResponse.builder()
                                        .scanId(scan.getScanId())
                                        .patientId(patientId)
                                        .storageUri(storageUri)
                                        .mimetype(mimetype)
                                        .checksum(checksum)
                                        .metadata(scan.getMetadata())
                                        .uploadedBy(doctorId)
                                        .uploadedAt(scan.getUploadedAt())
                                        .build();
                } catch (io.minio.errors.ErrorResponseException e) {
                        log.error("MinIO error during upload: {} - {}", e.errorResponse().code(),
                                        e.errorResponse().message(), e);
                        throw new RuntimeException("Failed to upload scan to MinIO: " + e.getMessage(), e);
                } catch (java.net.ConnectException e) {
                        log.error("Cannot connect to MinIO server at {}", minIOProperties.getEndpoint(), e);
                        throw new RuntimeException("MinIO server is not accessible. Please ensure MinIO is running at "
                                        + minIOProperties.getEndpoint(), e);
                } catch (Exception e) {
                        log.error("Failed to upload scan: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to upload scan: " + e.getMessage(), e);
                }
        }

        public ScanResponse getScan(UUID scanId, UUID doctorId) {
                EcgScan scan = scanRepository.findById(scanId)
                                .orElseThrow(() -> new NotFoundException("Scan not found"));

                // Check access to patient
                patientAccessRepository.findRoleByPatientIdAndDoctorId(scan.getPatient().getPatientId(), doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this scan"));

                return ScanResponse.builder()
                                .scanId(scan.getScanId())
                                .patientId(scan.getPatient().getPatientId())
                                .storageUri(scan.getStorageUri())
                                .mimetype(scan.getMimetype())
                                .checksum(scan.getChecksum())
                                .metadata(scan.getMetadata())
                                .uploadedBy(scan.getUploadedBy() != null ? scan.getUploadedBy().getDoctorId() : null)
                                .uploadedAt(scan.getUploadedAt())
                                .build();
        }

        public InputStream downloadScan(UUID scanId, UUID doctorId) {
                EcgScan scan = scanRepository.findById(scanId)
                                .orElseThrow(() -> new NotFoundException("Scan not found"));

                // Check access
                patientAccessRepository.findRoleByPatientIdAndDoctorId(scan.getPatient().getPatientId(), doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this scan"));

                try {
                        return minioClient.getObject(GetObjectArgs.builder()
                                        .bucket(minIOProperties.getBucket())
                                        .object(scan.getStorageUri())
                                        .build());
                } catch (Exception e) {
                        log.error("Failed to download scan", e);
                        throw new RuntimeException("Failed to download scan: " + e.getMessage());
                }
        }

        public com.ecgcare.backend.dto.response.PageResponse<ScanResponse> listPatientScans(UUID patientId,
                        UUID doctorId,
                        int page, int size) {
                // Check access
                patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this patient"));

                Pageable pageable = PageRequest.of(page, size);
                Page<EcgScan> scans = scanRepository.findByPatientId(patientId, pageable);

                List<ScanResponse> scanResponses = scans.getContent().stream()
                                .map(scan -> ScanResponse.builder()
                                                .scanId(scan.getScanId())
                                                .mimetype(scan.getMimetype())
                                                .uploadedAt(scan.getUploadedAt())
                                                .build())
                                .collect(Collectors.toList());

                com.ecgcare.backend.dto.response.PageResponse.PaginationInfo pagination = com.ecgcare.backend.dto.response.PageResponse.PaginationInfo
                                .builder()
                                .page(scans.getNumber())
                                .size(scans.getSize())
                                .totalElements(scans.getTotalElements())
                                .totalPages(scans.getTotalPages())
                                .build();

                return com.ecgcare.backend.dto.response.PageResponse.<ScanResponse>builder()
                                .content(scanResponses)
                                .pagination(pagination)
                                .build();
        }

        @Transactional
        public void deleteScan(UUID scanId, UUID doctorId) {
                EcgScan scan = scanRepository.findById(scanId)
                                .orElseThrow(() -> new NotFoundException("Scan not found"));

                // Check access
                patientAccessRepository.findRoleByPatientIdAndDoctorId(scan.getPatient().getPatientId(), doctorId)
                                .orElseThrow(() -> new ForbiddenException("No access to this scan"));

                try {
                        // Delete from MinIO
                        minioClient.removeObject(RemoveObjectArgs.builder()
                                        .bucket(minIOProperties.getBucket())
                                        .object(scan.getStorageUri())
                                        .build());
                } catch (Exception e) {
                        log.error("Failed to delete scan from MinIO", e);
                }

                scanRepository.delete(scan);
                auditService.logAction("delete", "scan", scanId, doctorId, null, null);
        }
}
