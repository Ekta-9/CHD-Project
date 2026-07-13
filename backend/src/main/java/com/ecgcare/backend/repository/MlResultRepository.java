package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.MlResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MlResultRepository extends JpaRepository<MlResult, UUID> {
    @Query("SELECT m FROM MlResult m WHERE m.patient.patientId = :patientId ORDER BY m.createdAt DESC")
    Page<MlResult> findByPatientId(@Param("patientId") UUID patientId, Pageable pageable);
}









