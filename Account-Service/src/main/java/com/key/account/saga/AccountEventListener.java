package com.key.account.saga;

import com.key.account.dto.BalanceUpdateRequest;
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

    Logger logger = LoggerFactory.getLogger(AccountEventListener.class);
    @Autowired
    private AccountService service;

    // Handles deposit/transfer credits via Saga
    @KafkaListener(topics = {"deposit-credited", "transfer-completed"}, groupId = "account-group")
    public void handleDepositOrTransferEvent(ConsumerRecord<String, Object> record) {
        Map<String, Object> event = (Map<String, Object>) record.value();
        String accountId = (String) event.get("accountId");
        BigDecimal amount = (BigDecimal) event.get("amount");

        //Trigger to saga update
        service.updateBalanceAsync(accountId, new BalanceUpdateRequest(amount, "Event update")).join();
        logger.info("Consumed deposit/transfer event for :" + accountId + ": +\" + amount)");
    }

    @KafkaListener(topics = {"account-rollback", "transfer-rollback"}, groupId = "account-group")
    public void handleRollbackEvent(ConsumerRecord<String, Object> record) {
        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) record.value();
        String accountId = (String) event.get("accountId");
        BigDecimal amount = new BigDecimal(event.get("amount").toString());

        service.updateBalanceAsync(accountId, new BalanceUpdateRequest(amount, "Rollback")).join();
    }
}
