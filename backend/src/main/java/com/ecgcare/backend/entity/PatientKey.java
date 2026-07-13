package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "patient_key")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(PatientKeyId.class)
public class PatientKey {
    @Id
    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Id
    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "wrapping_scheme", nullable = false)
    private String wrappingScheme;

    @Column(name = "dek_enc", nullable = false, columnDefinition = "bytea")
    private byte[] dekEnc;

    @Column(name = "dek_iv", nullable = false, columnDefinition = "bytea")
    private byte[] dekIv;

    @Column(name = "dek_tag", nullable = false, columnDefinition = "bytea")
    private byte[] dekTag;
}









