package com.key.deposite.controller;

import com.key.deposite.dto.DepositRequest;
import com.key.deposite.dto.DepositResponse;
import com.key.deposite.dto.TransactionHistoryResponse;
import com.key.deposite.services.DepositService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/deposits")
public class DepositController {

    @Autowired
    private DepositService depositService;

    /**
     * POST /deposits/credit/{accountId}
     * Credits amount to deposit account (async)
     */
    @PostMapping("/credit/{accountId}")
    public CompletableFuture<ResponseEntity<DepositResponse>> creditDeposit(
            @PathVariable String accountId,
            @RequestBody DepositRequest request) {

        return depositService.creditDepositAsync(accountId, request)
                .thenApply(newBalance -> {
                    DepositResponse response = new DepositResponse();
                    response.setMessage("Credited successfully");
                    response.setNewBalance(newBalance);
                    return ResponseEntity.ok(response);
                })
                .exceptionally(ex -> {
                    DepositResponse errorResponse = new DepositResponse();
                    errorResponse.setMessage("Credit failed: " + ex.getMessage());
                    errorResponse.setNewBalance(null);
                    return ResponseEntity.badRequest().body(errorResponse);
                });
    }

    /**
     * GET /deposits/{accountId}/history?limit=10
     * Returns recent transaction history
     */
    @GetMapping("/{accountId}/history")
    public ResponseEntity<List<TransactionHistoryResponse>> getHistory(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "10") int limit) {

        List<TransactionHistoryResponse> history = depositService.getTransactionHistory(accountId, limit);
        return ResponseEntity.ok(history);
    }

    /**
     * GET /deposits/{accountId}/balance
     * Returns available balance (ledger - holds)
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String accountId) {
        BigDecimal balance = depositService.getAvailableBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    /**
     * POST /deposits/debit/{accountId}
     * Debits amount (async, checks available balance)
     */
    @PostMapping("/debit/{accountId}")
    public CompletableFuture<ResponseEntity<String>> debitDeposit(
            @PathVariable String accountId,
            @RequestBody DepositRequest request) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                depositService.debitDepositAsync(accountId, request.getAmount());
                return ResponseEntity.ok("Debited successfully");
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Debit failed: " + e.getMessage());
            }
        });
    }
}