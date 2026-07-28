package com.ecgcare.backend.service;

import com.ecgcare.backend.entity.AuditLog;
import com.ecgcare.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void logActionSavesFullyPopulatedEntry() {
        UUID entityId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Map<String, Object> details = Map.of("reason", "test");

        auditService.logAction("create", "patient", entityId, doctorId, sessionId, details);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getAction()).isEqualTo("create");
        assertThat(saved.getEntityType()).isEqualTo("patient");
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getDoctor().getDoctorId()).isEqualTo(doctorId);
        assertThat(saved.getSession().getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getDetails()).isEqualTo(details);
    }

    @Test
    void logActionWithoutDoctorOrSessionLeavesThemNull() {
        auditService.logAction("read", "scan", UUID.randomUUID(), null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        assertThat(captor.getValue().getDoctor()).isNull();
        assertThat(captor.getValue().getSession()).isNull();
        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    void repositoryFailureIsSwallowed() {
        when(auditLogRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> auditService.logAction("update", "patient",
                UUID.randomUUID(), UUID.randomUUID(), null, null))
                .doesNotThrowAnyException();
    }
}
