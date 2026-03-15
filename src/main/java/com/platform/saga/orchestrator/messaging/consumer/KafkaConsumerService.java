package com.platform.saga.orchestrator.messaging.consumer;

import com.platform.saga.orchestrator.event.InventoryReleaseFailedEvent;
import com.platform.saga.orchestrator.event.InventoryReleasedEvent;
import com.platform.saga.orchestrator.event.InventoryReservationFailedEvent;
import com.platform.saga.orchestrator.event.InventoryReservedEvent;
import com.platform.saga.orchestrator.event.OrderCreatedEvent;
import com.platform.saga.orchestrator.event.PaymentProcessedEvent;
import com.platform.saga.orchestrator.event.PaymentProcessingFailedEvent;
import com.platform.saga.orchestrator.service.SagaService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class KafkaConsumerService {

  private final SagaService sagaService;

  @KafkaListener(topics = "${topics.order.created}", groupId = "${spring.kafka.consumer.group-id}")
  public void onOrderCreated(OrderCreatedEvent event) {
    log.info("Received order.created for orderId={}", event.getOrderId());
    sagaService.handleOrderCreated(event);
  }

  @KafkaListener(
      topics = "${topics.inventory.reserved}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void onInventoryReserved(InventoryReservedEvent event) {
    log.info("Received order.inventory.reserved for orderId={}", event.getOrderId());
    sagaService.handleInventoryReserved(event);
  }

  @KafkaListener(
      topics = "${topics.inventory.failed}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void onInventoryReservationFailed(InventoryReservationFailedEvent event) {
    log.warn("Received order.inventory.failed for orderId={}", event.getOrderId());
    sagaService.handleInventoryReservationFailed(event);
  }

  @KafkaListener(
      topics = "${topics.payment.processed}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void onPaymentProcessed(PaymentProcessedEvent event) {
    log.info("Received order.payment.processed for orderId={}", event.getOrderId());
    sagaService.handlePaymentProcessed(event);
  }

  @KafkaListener(topics = "${topics.payment.failed}", groupId = "${spring.kafka.consumer.group-id}")
  public void onPaymentFailed(PaymentProcessingFailedEvent event) {
    log.warn("Received order.payment.failed for orderId={}", event.getOrderId());
    sagaService.handlePaymentFailed(event);
  }

  @KafkaListener(
      topics = "${topics.inventory.released}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void onInventoryReleased(InventoryReleasedEvent event) {
    log.info("Received order.inventory.released for orderId={}", event.getOrderId());
    sagaService.handleInventoryReleased(event);
  }

  @KafkaListener(
      topics = "${topics.inventory.release-failed}",
      groupId = "${spring.kafka.consumer.group-id}")
  public void onInventoryReleaseFailed(InventoryReleaseFailedEvent event) {
    log.warn("Received order.inventory.release.failed for orderId={}", event.getOrderId());
    sagaService.handleInventoryReleaseFailed(event);
  }
}
