package com.key.deposite.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionHistoryResponse {
    private String id;
    private BigDecimal amount;
    private String type;
    private String description;
    private LocalDateTime postedAt;
}
