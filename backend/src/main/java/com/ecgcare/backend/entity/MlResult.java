package com.ecgcare.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "ml_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "result_id")
    private UUID resultId;

    @ManyToOne
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne
    @JoinColumn(name = "scan_id")
    private EcgScan scan;

    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @Column(name = "predicted_label", nullable = false)
    private String predictedLabel;

    @Column(name = "class_probs", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> classProbs;

    @Column(name = "explanation_uri")
    private String explanationUri;

    @Column(name = "threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal threshold;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private Doctor createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}









