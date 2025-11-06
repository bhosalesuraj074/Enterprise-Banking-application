package com.key.deposite.services;

import com.key.deposite.client.AccountClient;
import com.key.deposite.dto.DepositRequest;
import com.key.deposite.dto.TransactionHistoryResponse;
import com.key.deposite.entity.DepositAccount;
import com.key.deposite.entity.DepositTransaction;
import com.key.deposite.enums.DepositType;
import com.key.deposite.enums.TransactionType;
import com.key.deposite.exception.AccountNotFoundException;
import com.key.deposite.exception.InvalidAccountBalanceException;
import com.key.deposite.repository.DepositAccountRepository;
import com.key.deposite.repository.DepositTransactionRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
public class DepositService {
    private final DepositAccountRepository accountRepository;
    private final DepositTransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DepositService(DepositAccountRepository accountRepository, DepositTransactionRepository transactionRepository, AccountClient accountClient, KafkaTemplate<String, Object> kafkaTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountClient = accountClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    @Transactional
    public CompletableFuture<BigDecimal> creditDepositAsync(String accountId, DepositRequest request) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        BigDecimal currentBalance = accountClient.getBalance(accountId);
                        System.out.println("Account balance fetched: " + currentBalance);
                        return currentBalance;
                    } catch (feign.FeignException.NotFound ex) {
                        System.out.println("Account not found, treating as new: " + accountId);
                        return BigDecimal.ZERO;
                    } catch (Exception ex) {
                        throw new RuntimeException("Account validation failed: " + ex.getMessage(), ex);
                    }
                }).orTimeout(60, TimeUnit.SECONDS)  // Timeout after 60s
                .thenCompose(currentBalance -> {
                    if (currentBalance.compareTo(BigDecimal.ZERO) < 0) {
                        throw new InvalidAccountBalanceException("Invalid account balance");
                    }

                    DepositAccount depositAccount = accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                            .orElseGet(() -> createDepositAccount(accountId));

                    BigDecimal preciseAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newBalance = depositAccount.getBalance().add(preciseAmount);
                    depositAccount.setBalance(newBalance);
                    depositAccount.setAvailableBalance(depositAccount.getAvailableBalance().add(preciseAmount));
                    accountRepository.save(depositAccount);

                    DepositTransaction transaction = new DepositTransaction();
                    transaction.setAccountId(accountId);
                    transaction.setAmount(preciseAmount);
                    transaction.setType(TransactionType.CREDIT);
                    transaction.setDescription(request.getDescription());
                    transaction.setReferenceId(request.getReferenceId());
                    transaction.setStatus("POSTED");
                    transaction.setAccount(depositAccount);
                    transactionRepository.save(transaction);

                    Map<String, Object> event = new HashMap<>();
                    event.put("accountId", accountId);
                    event.put("amount", preciseAmount.toString());
                    event.put("type", "CREDITED");
                    event.put("currency", "INR");
                    event.put("timestamp", LocalDateTime.now().toString());
                    kafkaTemplate.send("deposit-credited", accountId, event);
                    System.out.println("Event published to deposit-credited for " + accountId);

                    return CompletableFuture.completedFuture(newBalance);
                }).exceptionally(ex -> {
                    if (ex.getCause() instanceof TimeoutException) {
                        // Timeout → Trigger rollback
                        publishRollback(accountId, request.getAmount(), request.getReferenceId());
                        return BigDecimal.ZERO;  // Fallback
                    }
                    throw new RuntimeException("Credit failed: " + ex.getMessage(), ex);
                });
    }

    private void publishRollback(String accountId, BigDecimal amount, String referenceId) {
        Map<String, Object> rollbackEvent = new HashMap<>();
        rollbackEvent.put("accountId", accountId);
        rollbackEvent.put("amount", amount.toString());
        rollbackEvent.put("type", "ROLLBACK_CREDIT");
        rollbackEvent.put("referenceId", referenceId);
        rollbackEvent.put("timestamp", LocalDateTime.now().toString());
        rollbackEvent.put("ttl", 86400L);  // 24hr in seconds
        kafkaTemplate.send("deposit-rollback", accountId, rollbackEvent);
        System.out.println("Rollback event published for " + accountId);
    }

    private DepositAccount createDepositAccount(String accountId) {
        DepositAccount depositAccount = new DepositAccount();
        depositAccount.setAccountId(accountId);
        depositAccount.setType(DepositType.CHECKING);  // Default
        depositAccount.setBalance(BigDecimal.ZERO);
        depositAccount.setAvailableBalance(BigDecimal.ZERO);
        return accountRepository.save(depositAccount);
    }

    public List<TransactionHistoryResponse> getTransactionHistory(String accountId, int limit) {
        System.out.println("Fetching history for " + accountId + ", limit: " + limit);
        List<DepositTransaction> transactionHistory = transactionRepository.findRecentByAccountId(accountId);
        System.out.println("Raw transactions found: " + transactionHistory.size());

        List<TransactionHistoryResponse> list = transactionHistory.stream()
                .limit(limit)
                .map(transaction -> {
                    TransactionHistoryResponse transactionResponse = new TransactionHistoryResponse();
                    transactionResponse.setId(transaction.getId().toString());
                    transactionResponse.setAmount(transaction.getAmount());
                    transactionResponse.setType(transaction.getType().toString());
                    transactionResponse.setDescription(transaction.getDescription());
                    transactionResponse.setPostedAt(transaction.getPostedAt());
                    return transactionResponse;
                })
                .collect(Collectors.toList());
        System.out.println("Mapped responses: " + list.size());
        return list;
    }

    public BigDecimal getAvailableBalance(String accountId) {
        DepositAccount depositAccount = accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return depositAccount.getAvailableBalance();
    }

    @Async
    @Transactional
    public void debitDepositAsync(String accountId, BigDecimal amount) {
        DepositAccount depositAccount = accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        BigDecimal preciseAmount = amount.setScale(2, RoundingMode.HALF_UP);
        if (depositAccount.getAvailableBalance().compareTo(preciseAmount) < 0) {
            throw new InvalidAccountBalanceException("Insufficient balance");
        }

        BigDecimal newBalance = depositAccount.getBalance().subtract(preciseAmount);
        depositAccount.setBalance(newBalance);
        depositAccount.setAvailableBalance(depositAccount.getAvailableBalance().subtract(preciseAmount));
        accountRepository.save(depositAccount);

        DepositTransaction transaction = new DepositTransaction();
        transaction.setAccountId(accountId);
        transaction.setAmount(preciseAmount.negate());
        transaction.setType(TransactionType.DEBIT);
        transaction.setStatus("POSTED");
        transaction.setAccount(depositAccount);
        transactionRepository.save(transaction);

        Map<String, Object> event = new HashMap<>();
        event.put("accountId", accountId);
        event.put("amount", preciseAmount.negate().toString());
        event.put("type", "DEBITED");
        event.put("currency", "INR");
        event.put("timestamp", LocalDateTime.now().toString());
        kafkaTemplate.send("deposit-debited", accountId, event);
        System.out.println("Event published to deposit-debited for " + accountId);
    }
}