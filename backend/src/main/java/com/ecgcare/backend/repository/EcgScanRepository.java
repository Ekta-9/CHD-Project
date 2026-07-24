package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.EcgScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EcgScanRepository extends JpaRepository<EcgScan, UUID> {
    @Query("SELECT s FROM EcgScan s WHERE s.patient.patientId = :patientId ORDER BY s.uploadedAt DESC")
    Page<EcgScan> findByPatientId(@Param("patientId") UUID patientId, Pageable pageable);

    // A scan is "pending" if it's been uploaded for a patient this doctor can
    // access, but no ML result exists for it yet.
    @Query("SELECT COUNT(s) FROM EcgScan s " +
            "WHERE s.patient.patientId IN (SELECT pa.patient.patientId FROM PatientAccess pa WHERE pa.doctor.doctorId = :doctorId) " +
            "AND NOT EXISTS (SELECT 1 FROM MlResult m WHERE m.scan.scanId = s.scanId)")
    long countPendingByDoctorId(@Param("doctorId") UUID doctorId);
}









