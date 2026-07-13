package com.ecgcare.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientAccessId implements Serializable {
    private UUID doctor;
    private UUID patient;
}


