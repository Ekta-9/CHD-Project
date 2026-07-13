package com.ecgcare.backend.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PredictRequest {
    private String modelVersion = "v1.0";
    private BigDecimal threshold = new BigDecimal("0.5");
}


