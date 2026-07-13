package com.ecgcare.backend.repository;

import com.ecgcare.backend.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DraftRepository extends JpaRepository<Draft, UUID> {
    Optional<Draft> findByDraftIdAndDoctor_DoctorId(UUID draftId, UUID doctorId);
}


