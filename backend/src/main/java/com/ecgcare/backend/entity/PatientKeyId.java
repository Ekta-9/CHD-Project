package com.ecgcare.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientKeyId implements Serializable {
    private UUID patient;
    private UUID doctor;
}


