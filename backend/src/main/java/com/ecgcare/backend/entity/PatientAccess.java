package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patient_access")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PatientAccessId.class)
public class PatientAccess {
    @Id
    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Id
    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccessRole role;

    @ManyToOne
    @JoinColumn(name = "granted_by")
    private Doctor grantedBy;

    @Column(name = "granted_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime grantedAt = OffsetDateTime.now();

    public enum AccessRole {
        owner, editor, viewer
    }
}









