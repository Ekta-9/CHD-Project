package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ecg_scan")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EcgScan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "scan_id")
    private UUID scanId;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(name = "storage_uri", nullable = false)
    private String storageUri;

    @Column(name = "mimetype", nullable = false)
    private String mimetype;

    @ManyToOne
    @JoinColumn(name = "uploaded_by")
    private Doctor uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
}