package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.DoctorAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DoctorAuthRepository extends JpaRepository<DoctorAuth, UUID> {
}


