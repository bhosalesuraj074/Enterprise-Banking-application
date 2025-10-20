package com.key.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceUpdateRequest {
    private BigDecimal amount;  // Positive for credit, negative for debit
    private String description;
}
