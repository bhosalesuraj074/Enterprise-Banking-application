package com.key.deposite.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@FeignClient(name = "account-service", url = "${feign.account-service.url}")
public interface AccountClient {

    @GetMapping("/accounts/{id}/balance")
    BigDecimal getBalance(@PathVariable("id") String id);  // ← Sync return
}
