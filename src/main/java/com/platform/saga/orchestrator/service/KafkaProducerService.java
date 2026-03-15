package com.platform.saga.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaProducerService {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void send(String topic, String key, Object event) {
    kafkaTemplate
        .send(topic, key, event)
        .whenComplete(
            (result, ex) -> {
              if (ex != null) {
                log.error("Failed to send event to topic {}: {}", topic, ex.getMessage());
              } else {
                log.info(
                    "Event sent to topic {} | partition: {} | offset: {}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
              }
            });
  }
}
