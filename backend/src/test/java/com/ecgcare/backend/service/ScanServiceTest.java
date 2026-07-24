package com.ecgcare.backend.service;

import com.ecgcare.backend.config.MinIOProperties;
import com.ecgcare.backend.dto.response.PageResponse;
import com.ecgcare.backend.dto.response.ScanResponse;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.EcgScan;
import com.ecgcare.backend.entity.Patient;
import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.exception.ForbiddenException;
import com.ecgcare.backend.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private com.ecgcare.backend.repository.EcgScanRepository scanRepository;
    @Mock
    private com.ecgcare.backend.repository.PatientRepository patientRepository;
    @Mock
    private com.ecgcare.backend.repository.DoctorRepository doctorRepository;
    @Mock
    private com.ecgcare.backend.repository.PatientAccessRepository patientAccessRepository;
    @Mock
    private S3Client s3Client;
    @Mock
    private AuditService auditService;

    private MinIOProperties minIOProperties;
    private ScanService scanService;

    private final UUID doctorId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID scanId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        minIOProperties = new MinIOProperties();
        minIOProperties.setBucket("test-bucket");
        scanService = new ScanService(scanRepository, patientRepository, doctorRepository,
                patientAccessRepository, s3Client, minIOProperties, auditService);
    }

    private Doctor doctor() {
        return Doctor.builder().doctorId(doctorId).email("doc@example.com")
                .fullName("Dr. Test").isActive(true).build();
    }

    private Patient patient() {
        return Patient.builder().patientId(patientId).encPayload(new byte[1])
                .encPayloadIv(new byte[1]).encPayloadTag(new byte[1]).build();
    }

    private EcgScan scan() {
        return EcgScan.builder()
                .scanId(scanId)
                .patient(patient())
                .storageUri(patientId + "/" + scanId + "/ecg.png")
                .mimetype("image/png")
                .uploadedBy(doctor())
                .checksum("sha256:abc")
                .metadata(Map.of("notes", "baseline"))
                .build();
    }

    private void grantAccess() {
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.of(PatientAccess.AccessRole.owner));
    }

    // ---------- uploadScan ----------

    @Test
    void uploadScanStoresFileAndSavesMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png",
                new byte[] { 1, 2, 3, 4 });

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient()));
        grantAccess();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));
        when(scanRepository.save(any(EcgScan.class))).thenAnswer(inv -> {
            EcgScan s = inv.getArgument(0);
            s.setScanId(scanId);
            return s;
        });

        ScanResponse response = scanService.uploadScan(file, patientId, doctorId, Map.of("notes", "first"));

        assertThat(response.getScanId()).isEqualTo(scanId);
        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getMimetype()).isEqualTo("image/png");
        assertThat(response.getStorageUri()).contains(patientId.toString()).endsWith("/ecg.png");
        assertThat(response.getChecksum()).startsWith("sha256:");
        assertThat(response.getMetadata()).containsEntry("notes", "first");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");

        verify(auditService).logAction(eq("upload"), eq("scan"), eq(scanId), eq(doctorId), any(), any());
    }

    @Test
    void uploadScanWithNullMetadataDefaultsToEmptyMap() {
        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png", new byte[] { 1 });

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient()));
        grantAccess();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));
        when(scanRepository.save(any(EcgScan.class))).thenAnswer(inv -> inv.getArgument(0));

        ScanResponse response = scanService.uploadScan(file, patientId, doctorId, null);

        assertThat(response.getMetadata()).isEmpty();
    }

    @Test
    void uploadScanRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png", new byte[0]);

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient()));
        grantAccess();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));

        assertThatThrownBy(() -> scanService.uploadScan(file, patientId, doctorId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("File is empty");
        verify(scanRepository, never()).save(any());
    }

    @Test
    void uploadScanRejectsNonImageFile() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf",
                new byte[] { 1, 2 });

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient()));
        grantAccess();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));

        assertThatThrownBy(() -> scanService.uploadScan(file, patientId, doctorId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Only images are allowed");
    }

    @Test
    void uploadScanFailsWhenPatientMissing() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png", new byte[] { 1 });

        assertThatThrownBy(() -> scanService.uploadScan(file, patientId, doctorId, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void uploadScanForbiddenWithoutAccess() {
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png", new byte[] { 1 });

        assertThatThrownBy(() -> scanService.uploadScan(file, patientId, doctorId, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void uploadScanWrapsStorageErrors() {
        MockMultipartFile file = new MockMultipartFile("file", "ecg.png", "image/png", new byte[] { 1 });

        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient()));
        grantAccess();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor()));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("storage down").build());

        assertThatThrownBy(() -> scanService.uploadScan(file, patientId, doctorId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to upload scan to storage");
    }

    // ---------- getScan ----------

    @Test
    void getScanReturnsMetadataForAuthorizedDoctor() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        grantAccess();

        ScanResponse response = scanService.getScan(scanId, doctorId);

        assertThat(response.getScanId()).isEqualTo(scanId);
        assertThat(response.getPatientId()).isEqualTo(patientId);
        assertThat(response.getUploadedBy()).isEqualTo(doctorId);
        assertThat(response.getMetadata()).containsEntry("notes", "baseline");
    }

    @Test
    void getScanThrowsWhenMissing() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.getScan(scanId, doctorId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getScanForbiddenWithoutAccess() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.getScan(scanId, doctorId))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- downloadScan ----------

    @Test
    void downloadScanStreamsObjectFromStorage() throws Exception {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        grantAccess();
        byte[] content = { 9, 8, 7 };
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream(content))));

        InputStream stream = scanService.downloadScan(scanId, doctorId);

        assertThat(stream.readAllBytes()).isEqualTo(content);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo(scan().getStorageUri());
    }

    @Test
    void downloadScanWrapsStorageFailure() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        grantAccess();
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("no such key").build());

        assertThatThrownBy(() -> scanService.downloadScan(scanId, doctorId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to download scan");
    }

    @Test
    void downloadScanForbiddenWithoutAccess() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.downloadScan(scanId, doctorId))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- listPatientScans / pending count ----------

    @Test
    void listPatientScansReturnsPage() {
        grantAccess();
        when(scanRepository.findByPatientId(eq(patientId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scan()), PageRequest.of(0, 20), 1));

        PageResponse<ScanResponse> response = scanService.listPatientScans(patientId, doctorId, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getScanId()).isEqualTo(scanId);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
    }

    @Test
    void listPatientScansForbiddenWithoutAccess() {
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.listPatientScans(patientId, doctorId, 0, 20))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void pendingScanCountDelegatesToRepository() {
        when(scanRepository.countPendingByDoctorId(doctorId)).thenReturn(4L);

        assertThat(scanService.getPendingScanCount(doctorId)).isEqualTo(4L);
    }

    // ---------- deleteScan ----------

    @Test
    void deleteScanRemovesObjectAndRecord() {
        EcgScan scan = scan();
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
        grantAccess();

        scanService.deleteScan(scanId, doctorId);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo(scan.getStorageUri());
        verify(scanRepository).delete(scan);
        verify(auditService).logAction(eq("delete"), eq("scan"), eq(scanId), eq(doctorId), any(), any());
    }

    @Test
    void deleteScanStillDeletesRecordWhenStorageFails() {
        EcgScan scan = scan();
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
        grantAccess();
        doThrow(S3Exception.builder().statusCode(500).message("storage down").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        scanService.deleteScan(scanId, doctorId);

        verify(scanRepository).delete(scan);
    }

    @Test
    void deleteScanForbiddenWithoutAccess() {
        when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan()));
        when(patientAccessRepository.findRoleByPatientIdAndDoctorId(patientId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.deleteScan(scanId, doctorId))
                .isInstanceOf(ForbiddenException.class);
        verify(scanRepository, never()).delete(any());
    }
}
