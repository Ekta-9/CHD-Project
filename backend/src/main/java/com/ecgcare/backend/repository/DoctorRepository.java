package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    Optional<Doctor> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}


