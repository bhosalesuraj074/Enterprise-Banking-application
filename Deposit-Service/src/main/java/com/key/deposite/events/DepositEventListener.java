package com.key.deposite.events;

import com.key.deposite.dto.DepositRequest;
import com.key.deposite.entity.DepositAccount;
import com.key.deposite.enums.DepositStatus;
import com.key.deposite.enums.DepositType;
import com.key.deposite.repository.DepositAccountRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class DepositEventListener {

    private static final Logger log = LoggerFactory.getLogger(DepositEventListener.class);
    private final DepositAccountRepository repo;

    public DepositEventListener(DepositAccountRepository repo) {
        this.repo = repo;
    }

    /**
     * Listens to the **account-updated** topic.
     * Payload format (sent by Account Service):
     * ->
     *   { "accountId": "KEY123ABC",
     *   "type": "CREATED|UPDATED|CLOSED",   // we care about CREATED & UPDATED
     *   "balance": "1500.00",
     *   "currency": "INR"
     * }
     */
    @KafkaListener(topics = "account-updated", groupId = "deposit-group")
    public void onAccountEvent(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) record.value();

        String accountId = (String) payload.get("accountId");
        String type      = (String) payload.get("type");
        String balanceStr= (String) payload.getOrDefault("balance", "0");

        if (accountId == null || type == null) {
            log.warn("Invalid account-updated payload – missing fields: {}", payload);
            return;
        }

        // -------------------------------------------------
        // 1. Parse balance safely
        // -------------------------------------------------
        BigDecimal balance;
        try {
            balance = new BigDecimal(balanceStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid balance value '{}' for account {} – skipping", balanceStr, accountId);
            return;
        }

        // -------------------------------------------------
        // 2. Decide what to do
        // -------------------------------------------------
        switch (type) {
            case "CREATED", "UPDATED" -> syncDepositAccount(accountId, balance);
            case "CLOSED"            -> markDeleted(accountId);
            default                  -> log.debug("Ignoring event type {} for account {}", type, accountId);
        }
    }

    /** -------------------------------------------------
     *  CREATE or UPDATE the local DepositAccount
     *  ------------------------------------------------- */
    private void syncDepositAccount(String accountId, BigDecimal balance) {
        repo.findByAccountIdAndIsDeletedFalse(accountId)
                .ifPresentOrElse(
                        existing -> {
                            // ---- UPDATE ----
                            existing.setBalance(balance);
                            existing.setAvailableBalance(balance);
                            repo.save(existing);
                            log.info("DepositAccount {} balance UPDATED to {}", accountId, balance);
                        },
                        () -> {
                            // ---- CREATE ----
                            DepositAccount acc = new DepositAccount();
                            acc.setAccountId(accountId);
                            acc.setBalance(balance);
                            acc.setAvailableBalance(balance);
                            acc.setType(DepositType.CHECKING);
                            acc.setStatus(DepositStatus.ACTIVE);
                            repo.save(acc);
                            log.info("DepositAccount {} CREATED with balance {}", accountId, balance);
                        });
    }

    /** ---------------------
      Soft-delete when Account Service says CLOSED
    ----------------------- */
    private void markDeleted(String accountId) {
        repo.findByAccountIdAndIsDeletedFalse(accountId)
                .ifPresent(acc -> {
                    acc.setDeleted(true);
                    repo.save(acc);
                    log.info("DepositAccount {} marked DELETED (account closed)", accountId);
                });
    }
}