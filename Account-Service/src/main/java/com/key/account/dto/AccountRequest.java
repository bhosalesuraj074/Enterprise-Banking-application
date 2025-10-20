package com.key.account.dto;

import com.key.account.enums.AccountType;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AccountRequest {
    private String customerId;
    private AccountType type;
    private BigDecimal initialBalance = BigDecimal.ZERO;
}
