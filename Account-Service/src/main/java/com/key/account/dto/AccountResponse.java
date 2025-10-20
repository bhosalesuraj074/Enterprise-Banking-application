package com.key.account.dto;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class AccountResponse {
    private String accountId;
    private BigDecimal balance;
    private String status;
}
