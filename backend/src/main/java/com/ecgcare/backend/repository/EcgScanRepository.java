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
}









