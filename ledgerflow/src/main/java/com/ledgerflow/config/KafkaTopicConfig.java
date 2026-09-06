package com.ledgerflow.config;

import com.ledgerflow.events.KafkaPaymentEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "ledgerflow.events.transport", havingValue = "kafka")
public class KafkaTopicConfig {

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaPaymentEventPublisher.TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
