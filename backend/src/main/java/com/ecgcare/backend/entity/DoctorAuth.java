package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_auth")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAuth {
    @Id
    @Column(name = "doctor_id")
    private UUID doctorId;

    @OneToOne
    @JoinColumn(name = "doctor_id")
    @MapsId
    private Doctor doctor;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "last_password_reset")
    private OffsetDateTime lastPasswordReset;
}









