package com.key.account.client;

import com.key.account.dto.ValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "Deposit-Service")
public interface DepositClient {
    @PostMapping("/deposits/validate/{accountId}")
    ValidationResponse validateAccount(@RequestBody Map<String, Object> request);
}
