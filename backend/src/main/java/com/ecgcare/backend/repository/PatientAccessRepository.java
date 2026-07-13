package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.PatientAccess;
import com.ecgcare.backend.entity.PatientAccess.AccessRole;
import com.ecgcare.backend.entity.PatientAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientAccessRepository extends JpaRepository<PatientAccess, PatientAccessId> {
    Optional<PatientAccess> findByPatient_PatientIdAndDoctor_DoctorId(UUID patientId, UUID doctorId);

    @Query("SELECT pa FROM PatientAccess pa WHERE pa.patient.patientId = :patientId")
    List<PatientAccess> findByPatientId(@Param("patientId") UUID patientId);

    boolean existsByPatient_PatientIdAndDoctor_DoctorId(UUID patientId, UUID doctorId);

    @Query("SELECT pa.role FROM PatientAccess pa WHERE pa.patient.patientId = :patientId AND pa.doctor.doctorId = :doctorId")
    Optional<AccessRole> findRoleByPatientIdAndDoctorId(@Param("patientId") UUID patientId,
            @Param("doctorId") UUID doctorId);
}









