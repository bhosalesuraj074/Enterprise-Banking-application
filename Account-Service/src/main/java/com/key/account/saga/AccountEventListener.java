package com.key.account.saga;

import com.key.account.dto.BalanceUpdateRequest;
import com.key.account.repository.AccountRepository;
import com.key.account.service.AccountService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class AccountEventListener {

    private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);
    private final AccountRepository accountRepository;

    public AccountEventListener(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @KafkaListener(topics = {"deposit-credited", "deposit-debited"}, groupId = "account-group")
    public void handleDepositOrTransferEvent(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) record.value();

        String accountId = (String) payload.get("accountId");
        String amountStr = (String) payload.get("amount");          // ← String
        String type      = (String) payload.get("type");            // CREDITED / DEBITED

        if (accountId == null || amountStr == null || type == null) {
            log.warn("Invalid payload – missing fields: {}", payload);
            return;
        }

        BigDecimal amount = new BigDecimal(amountStr);              // ← safe conversion

        // DEBITED event sends negative amount → make it positive for subtraction
        if ("DEBITED".equalsIgnoreCase(type)) {
            amount = amount.abs();   // e.g. "-200.00" → "200.00"
        }

        updateBalance(accountId, amount, type);
    }

    private void updateBalance(String accountId, BigDecimal delta, String operation) {
        accountRepository.findByAccountIdAndIsDeletedFalse(accountId)
                .ifPresentOrElse(
                        account -> {
                            BigDecimal oldBal = account.getBalance();
                            BigDecimal newBal = oldBal.add("CREDITED".equalsIgnoreCase(operation) ? delta : delta.negate());
                            account.setBalance(newBal);
                            accountRepository.save(account);
                            log.info("[{}] Account {} balance {} → {}", operation, accountId, oldBal, newBal);
                        },
                        () -> log.warn("Account {} not found – ignoring {} event", accountId, operation)
                );
    }
}