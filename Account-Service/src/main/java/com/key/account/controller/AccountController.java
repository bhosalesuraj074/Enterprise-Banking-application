package com.key.account.controller;

import com.key.account.dto.AccountRequest;
import com.key.account.dto.AccountResponse;
import com.key.account.dto.BalanceUpdateRequest;
import com.key.account.entity.Account;
import com.key.account.service.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    @Autowired
    private AccountService accountService;

    // Creates new account
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountRequest accountRequest) {
        Account account = accountService.createAccount(accountRequest.getCustomerId(), accountRequest.getType(), accountRequest.getInitialBalance());
        AccountResponse response = new AccountResponse();
        response.setAccountId(account.getAccountId()) ;
        response.setBalance(account.getBalance());
        response.setStatus(account.getStatus().name());
        return ResponseEntity.ok(response);

    }

    // retrieve all the details of the account
    @GetMapping("/{id}")
    public ResponseEntity<Account> getAccount(@PathVariable String id) {
      return new ResponseEntity<>(accountService.getAccount(id), HttpStatus.OK);
    }

    //retrieve balance from the account
    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable String id) {
        return ResponseEntity.ok(accountService.getBalance(id));
    }

    @PutMapping("/{id}/balance")
    public CompletableFuture<ResponseEntity<String>> updateBalance(@PathVariable String id, @RequestBody BalanceUpdateRequest request) {
        return accountService.updateBalanceAsync(id, request)
                .thenApply(balance -> ResponseEntity.ok("Updated balance via Saga: " + balance))
                .exceptionally(ex -> ResponseEntity.badRequest().body("Saga failed: " + ex.getMessage()));
    }
}
