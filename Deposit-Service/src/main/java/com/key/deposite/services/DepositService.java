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
import jakarta.transaction.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
                // Sync Feign call – returns plain BigDecimal
                BigDecimal currentBalance = accountClient.getBalance(accountId);
                System.out.println("Account balance fetched: " + currentBalance);
                return currentBalance;
            } catch (feign.FeignException.NotFound ex) {
                System.out.println("Account not found, treating as new: " + accountId);
                return BigDecimal.ZERO;
            } catch (Exception ex) {
                throw new RuntimeException("Account validation failed: " + ex.getMessage(), ex);
            }
        }).thenCompose(currentBalance -> {
            // --- REST OF YOUR LOGIC (unchanged) ---
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

            return CompletableFuture.completedFuture(newBalance);
        });
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
        return transactionRepository.findRecentByAccountId(accountId).stream()
                .limit(limit)
                .map(tx -> {
                    TransactionHistoryResponse res = new TransactionHistoryResponse();
                    res.setId(tx.getId().toString());
                    res.setAmount(tx.getAmount());
                    res.setType(tx.getType().toString());
                    res.setDescription(tx.getDescription());
                    res.setPostedAt(tx.getPostedAt());
                    return res;
                })
                .toList();
    }
    //Retrieves available balance (subtracts holds)
    public BigDecimal getAvailableBalance(String accountId) {
        DepositAccount depositAccount = accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
        return  depositAccount.getAvailableBalance();
    }

    // Async debits deposit
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

        // Transaction
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