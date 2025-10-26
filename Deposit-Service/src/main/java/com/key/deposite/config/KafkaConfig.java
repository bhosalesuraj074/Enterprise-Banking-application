package com.key.deposite.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic depositCreditedTopic() {
        return TopicBuilder.name("deposit-credited").partitions(1).build();
    }

    @Bean
    public NewTopic depositDebitedTopic() {
        return TopicBuilder.name("deposit-debited").partitions(1).build();
    }

    @Bean
    public NewTopic depositRollbackTopic() {
        return TopicBuilder.name("deposit-rollback").partitions(1).build();
    }

}
