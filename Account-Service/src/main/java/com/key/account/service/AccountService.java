package com.key.account.service;

import com.key.account.dto.BalanceUpdateRequest;
import com.key.account.entity.Account;
import com.key.account.enums.AccountStatus;
import com.key.account.enums.AccountType;
import com.key.account.exception.AccountNotFoundException;
import com.key.account.repository.AccountRepository;
import com.key.account.saga.AccountSagaOrchestrator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class AccountService {
//    @Autowired
    private final AccountRepository accountRepository;

//    @Autowired
    private final AccountSagaOrchestrator sagaOrchestrator;

//    @Autowired
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AccountService(AccountRepository accountRepository, AccountSagaOrchestrator sagaOrchestrator, KafkaTemplate<String, Object> kafkaTemplate) {
        this.accountRepository = accountRepository;
        this.sagaOrchestrator = sagaOrchestrator;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Account createAccount(String customerId, AccountType type, BigDecimal initialBalance) {
        Account account = new Account();
        account.setAccountId("KEY" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        account.setCustomerId(customerId);
        account.setType(type);
        account.setBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);
        publishAccountEvent(saved.getAccountId(), "CREATED", saved.getBalance());
        return saved;

    }

    // get the account details specified account Id
    public Account getAccount(String accountId) {

        return accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: "+accountId));
    }

    // get the balance of the specified account id
    public BigDecimal getBalance(String accountId) {
        return getAccount(accountId).getBalance();
    }

    // Delegates to Saga for balance updates
    public CompletableFuture<BigDecimal> updateBalanceAsync(String accountId, BalanceUpdateRequest eventUpdate) {
        String updateId = "UPD" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        return sagaOrchestrator.orchestrateBalanceUpdateAsync(updateId, accountId, eventUpdate.getAmount())
                .thenApply(newBalance ->{
                   publishAccountEvent(accountId, "UPDATED", newBalance);
                   return newBalance;
                });
    }

    private void publishAccountEvent(String accountId, String eventType, BigDecimal balance) {
        Map<String, Object> event = new HashMap<>();
        event.put("accountId", accountId);
        event.put("type", eventType);
        event.put("balance", balance.toPlainString()); // ← CRITICAL: String!
        event.put("timestamp", LocalDateTime.now().toString());
        CompletableFuture<SendResult<String, Object>> send = kafkaTemplate.send("account-updated", accountId, event);
       send.thenAccept(result -> {
           System.out.println("Message sent successfully to Kafka: " + result.getRecordMetadata().offset());
       });
        send.exceptionally(ex -> {
            System.err.println("Failed to send message to Kafka: " + ex.getMessage());
            // Perform failure handling here (e.g., retry, alert, etc.)
            return null;
        });
    }

    @KafkaListener(topics = "deposit-rollback", groupId = "account-group")
    public void onDepositRollback(ConsumerRecord<String, Object> record) {
        Map<String, Object> event = (Map<String, Object>) record.value();
        String accountId = (String) event.get("accountId");
        BigDecimal amount = new BigDecimal(event.get("amount").toString());

        accountRepository.findByAccountIdAndIsDeletedFalse(accountId).ifPresent(account -> {
            account.setBalance(account.getBalance().add(amount)); // reverse
            accountRepository.save(account);
        });
    }

}
