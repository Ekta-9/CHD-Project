package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID sessionId;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "login_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime loginAt = OffsetDateTime.now();

    @Column(name = "last_activity_at", nullable = false)
    @Builder.Default
    private OffsetDateTime lastActivityAt = OffsetDateTime.now();

    @Column(name = "logout_at")
    private OffsetDateTime logoutAt;

    @Column(name = "ended_by")
    @Enumerated(EnumType.STRING)
    private SessionEndReason endedBy;

    @Column(name = "ip")
    private InetAddress ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    public enum SessionEndReason {
        logout, timeout
    }
}









