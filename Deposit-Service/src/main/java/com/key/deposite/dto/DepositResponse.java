package com.key.deposite.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepositResponse {
    private String message;
    private BigDecimal newBalance;
}
