package com.ecgcare.backend.service;

import com.ecgcare.backend.entity.AuditLog;
import com.ecgcare.backend.entity.Doctor;
import com.ecgcare.backend.entity.Session;
import com.ecgcare.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void logAction(String action, String entityType, UUID entityId, UUID doctorId, UUID sessionId,
            Map<String, Object> details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .build();

            if (doctorId != null) {
                Doctor doctor = new Doctor();
                doctor.setDoctorId(doctorId);
                auditLog.setDoctor(doctor);
            }

            if (sessionId != null) {
                Session session = new Session();
                session.setSessionId(sessionId);
                auditLog.setSession(session);
            }

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log audit action: {}", action, e);
        }
    }
}









