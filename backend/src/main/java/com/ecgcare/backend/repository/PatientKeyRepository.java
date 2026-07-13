package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.PatientKey;
import com.ecgcare.backend.entity.PatientKeyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientKeyRepository extends JpaRepository<PatientKey, PatientKeyId> {
    Optional<PatientKey> findByPatient_PatientIdAndDoctor_DoctorId(UUID patientId, UUID doctorId);
}









