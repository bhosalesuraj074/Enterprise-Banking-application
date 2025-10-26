package com.key.deposite.events;

import com.key.deposite.dto.DepositRequest;
import com.key.deposite.entity.DepositAccount;
import com.key.deposite.enums.DepositStatus;
import com.key.deposite.enums.DepositType;
import com.key.deposite.repository.DepositAccountRepository;
import com.key.deposite.services.DepositService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class DepositEventListener {

    private static final Logger log = LoggerFactory.getLogger(DepositEventListener.class);

    @Autowired
    private DepositAccountRepository repo;

    @KafkaListener(topics = "account-updated", groupId = "deposit-group")
    public void onAccountCreated(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) record.value();
        if ("CREATED".equals(event.get("type"))) {
            String accountId = (String) event.get("accountId");
            BigDecimal balance = new BigDecimal(event.getOrDefault("balance", "0").toString());

            if (repo.findByAccountIdAndIsDeletedFalse(accountId).isEmpty()) {
                DepositAccount acc = new DepositAccount();
                acc.setAccountId(accountId);
                acc.setBalance(balance);
                acc.setAvailableBalance(balance);
                acc.setType(DepositType.CHECKING);
                acc.setStatus(DepositStatus.ACTIVE);
                repo.save(acc);
                log.info("DepositAccount created for: {}", accountId);
            }
        }
    }

    @KafkaListener(topics = "deposit-rollback", groupId = "deposit-group")
    public void onRollback(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) record.value();
        String accountId = (String) event.get("accountId");
        BigDecimal amount = new BigDecimal(event.get("amount").toString()).negate();

        repo.findByAccountIdAndIsDeletedFalse(accountId).ifPresent(acc -> {
            acc.setBalance(acc.getBalance().add(amount));
            acc.setAvailableBalance(acc.getAvailableBalance().add(amount));
            repo.save(acc);
            log.info("Rollback +{} applied to {}", amount, accountId);
        });
    }
}