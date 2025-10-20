package com.key.account.dto;

import lombok.Data;

@Data
public class ValidationResponse {
    private boolean valid;
    private String message;
}
