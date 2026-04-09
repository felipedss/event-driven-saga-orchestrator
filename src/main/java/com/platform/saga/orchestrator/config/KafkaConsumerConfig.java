package com.platform.saga.orchestrator.config;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.JacksonJsonMessageConverter;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  @Value("${spring.kafka.consumer.group-id}")
  private String groupId;

  @Autowired private KafkaTemplate<String, Object> kafkaTemplate;

  @Value("${kafka.dlq.backoff-interval-ms}")
  private long dlqBackoffIntervalMs;

  @Value("${kafka.dlq.max-retries}")
  private long dlqMaxRetries;

  @Bean
  public ConsumerFactory<String, String> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
  }

  @Bean
  public DefaultErrorHandler errorHandler() {
    DeadLetterPublishingRecoverer dlqRecoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate, (record, ex) -> new TopicPartition(record.topic() + ".DLQ", 0));
    return new DefaultErrorHandler(
        (record, ex) -> {
          log.error(
              "Retries exhausted, routing to DLQ: topic={}, partition={}, offset={}, key={}, cause={}",
              record.topic(),
              record.partition(),
              record.offset(),
              record.key(),
              ex.getMessage(),
              ex);
          dlqRecoverer.accept(record, ex);
        },
        new FixedBackOff(dlqBackoffIntervalMs, dlqMaxRetries));
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    // Spring uses the listener method parameter type to convert automatically
    factory.setRecordMessageConverter(new JacksonJsonMessageConverter());
    factory.setCommonErrorHandler(errorHandler());
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }
}
