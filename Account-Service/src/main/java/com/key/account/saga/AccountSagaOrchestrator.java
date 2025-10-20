package com.key.account.saga;

import com.key.account.entity.Account;
import com.key.account.enums.AccountStatus;
import com.key.account.exception.AccountDeactivatedException;
import com.key.account.exception.AccountNotFoundException;
import com.key.account.repository.AccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class AccountSagaOrchestrator {

    @Autowired
    private AccountRepository repository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Async
    @Transactional
    public CompletableFuture<BigDecimal> orchestrateBalanceUpdateAsync(String updateId, String accountId, BigDecimal amount) {

        try {
            // get the account from DB
            Optional<Account> optionalAccount = repository.findByAccountIdAndIsDeletedFalse(accountId);


            // Checking is account is present or not. if not throwing the exception
            if (optionalAccount.isEmpty()) {
                throw new AccountNotFoundException("Account not found: " + accountId);
            }

            // checking account status is active or not
            Account account = optionalAccount.get();
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountDeactivatedException("Account is not active:  " + accountId);
            }

//        INR precision: Manual scale=2 for paise
            BigDecimal preciseAmount = amount.setScale(2, RoundingMode.HALF_UP);
            BigDecimal newBalance = account.getBalance().add(preciseAmount);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("New balance cannot be negative");
            }

            // update in the database
            account.setBalance(newBalance);
            repository.save(account);

            // publish the event
            publishAccountEvent(updateId, accountId, "UPDATED", newBalance);

            return CompletableFuture.completedFuture(newBalance);

        } catch (RuntimeException e) {
            compensateBalanceUpdateAsync(updateId, accountId, amount, e.getMessage());
            throw new RuntimeException("Balance update failed: " + e.getMessage());
        }

    }

    private void publishAccountEvent(String updateId, String accountId, String eventType, BigDecimal newBalance) {
        Map<String, Object> event = new HashMap<>();
        event.put("updateId", updateId);
        event.put("accountId", accountId);
        event.put("balance", newBalance);
        event.put("currency", "INR");
        event.put("type", eventType);
        event.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("account-updated", event);


    }

    private void compensateBalanceUpdateAsync(String updateId, String accountId, BigDecimal amount, String reason) {
        Optional<Account> optAccount = repository.findByAccountIdAndIsDeletedFalse(accountId);
        if (optAccount.isEmpty()) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }

        if (optAccount.isPresent()) {
            Account account = optAccount.get();
            account.setBalance(account.getBalance().subtract(amount));
            repository.save(account);
        }

        Map<String, Object> rollBackEvent = new HashMap<>();
        rollBackEvent.put("updateId", updateId);
        rollBackEvent.put("accountId", accountId);
        rollBackEvent.put("balance", amount.negate());
        rollBackEvent.put("reason", reason);
        kafkaTemplate.send("account-rollback", accountId, rollBackEvent);
    }
}
