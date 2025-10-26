package com.key.account.saga;

import com.key.account.repository.AccountRepository;
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

    private final AccountRepository accountRepository;

    public DepositEventListener(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @KafkaListener(topics = "deposit-credited", groupId = "account-group")
    public void onDepositCredited(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) record.value();

        String accountId = (String) payload.get("accountId");
        BigDecimal amount = new BigDecimal((String)  payload.get("amount"));

        updateBalance(accountId, amount, "CREDIT");
    }

    @KafkaListener(topics = "deposit-debited", groupId = "account-group")
    public void onDepositDebited(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) record.value();

        String accountId = (String) payload.get("accountId");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString()).abs();

        updateBalance(accountId, amount.negate(), "DEBIT");
    }

    private void updateBalance(String accountId, BigDecimal delta, String operation) {
        accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                .ifPresentOrElse(
                        account -> {
                            BigDecimal newBal = account.getBalance().add(delta);
                            account.setBalance(newBal);
                            accountRepository.save(account);
                            log.info("[{}] Account {} balance {} → {}", operation, accountId,
                                    account.getBalance().subtract(delta), newBal);
                        },
                        () -> log.warn("Account {} not found – ignoring {} event", accountId, operation)
                );
    }
}
