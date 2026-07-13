package com.ecgcare.backend.entity;

import com.ecgcare.backend.converter.JsonMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "doctor_crypto")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorCrypto {
    @Id
    @Column(name = "doctor_id")
    private UUID doctorId;

    @OneToOne
    @JoinColumn(name = "doctor_id")
    @MapsId
    private Doctor doctor;

    @Column(name = "public_key", nullable = false, columnDefinition = "varbinary(2048)")
    private byte[] publicKey;

    @Column(name = "private_key_enc", nullable = false, columnDefinition = "varbinary(2048)")
    private byte[] privateKeyEnc;

    @Column(name = "private_key_salt", nullable = false, columnDefinition = "varbinary(64)")
    private byte[] privateKeySalt;

    @Column(name = "kek_params", nullable = false, length = 1000)
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> kekParams;
}
