package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "patient")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "anonymized_code", unique = true)
    private String anonymizedCode;

    @Column(name = "enc_payload", nullable = false, columnDefinition = "bytea")
    private byte[] encPayload;

    @Column(name = "enc_payload_iv", nullable = false, columnDefinition = "bytea")
    private byte[] encPayloadIv;

    @Column(name = "enc_payload_tag", nullable = false, columnDefinition = "bytea")
    private byte[] encPayloadTag;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}









