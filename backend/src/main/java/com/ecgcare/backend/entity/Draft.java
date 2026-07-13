package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "draft")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Draft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "draft_id")
    private UUID draftId;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "form_type", nullable = false)
    private String formType;

    @Column(name = "enc_payload", nullable = false, columnDefinition = "bytea")
    private byte[] encPayload;

    @Column(name = "enc_payload_iv", nullable = false, columnDefinition = "bytea")
    private byte[] encPayloadIv;

    @Column(name = "enc_payload_tag", nullable = false, columnDefinition = "bytea")
    private byte[] encPayloadTag;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}









