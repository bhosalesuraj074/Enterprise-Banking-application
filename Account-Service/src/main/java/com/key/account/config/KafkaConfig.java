package com.key.account.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic accountUpdateTopic() {
        return TopicBuilder.name("account-updated").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic accountRollbackTopic() {
        return TopicBuilder.name("account-rollback").partitions(3).replicas(1).build();
    }
}
