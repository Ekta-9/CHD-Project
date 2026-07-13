package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByAnonymizedCode(String anonymizedCode);

    @Query("SELECT p FROM Patient p JOIN PatientAccess pa ON p.patientId = pa.patient.patientId WHERE pa.doctor.doctorId = :doctorId")
    Page<Patient> findPatientsByDoctorId(@Param("doctorId") UUID doctorId, Pageable pageable);
}









