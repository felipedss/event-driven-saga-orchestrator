package com.platform.saga.orchestrator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Value("${kafka.dlq.partitions}")
  private int dlqPartitions;

  @Value("${kafka.dlq.replicas}")
  private int dlqReplicas;

  @Bean
  public NewTopic orderCreatedDlq() {
    return TopicBuilder.name("order.created.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic paymentProcessedDlq() {
    return TopicBuilder.name("order.payment.processed.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic paymentFailedDlq() {
    return TopicBuilder.name("order.payment.failed.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic inventoryReservedDlq() {
    return TopicBuilder.name("order.inventory.reserved.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic inventoryReservationFailedDlq() {
    return TopicBuilder.name("order.inventory.failed.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic inventoryReleasedDlq() {
    return TopicBuilder.name("order.inventory.released.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }

  @Bean
  public NewTopic inventoryReleaseFailedDlq() {
    return TopicBuilder.name("order.inventory.release.failed.DLQ")
        .partitions(dlqPartitions)
        .replicas(dlqReplicas)
        .build();
  }
}
