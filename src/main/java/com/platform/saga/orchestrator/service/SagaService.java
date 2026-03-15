package com.platform.saga.orchestrator.service;

import com.platform.saga.orchestrator.command.OrderCanceledCommand;
import com.platform.saga.orchestrator.command.OrderConfirmedCommand;
import com.platform.saga.orchestrator.command.ProcessPaymentCommand;
import com.platform.saga.orchestrator.command.ReleaseInventoryCommand;
import com.platform.saga.orchestrator.command.ReserveInventoryCommand;
import com.platform.saga.orchestrator.event.InventoryReleaseFailedEvent;
import com.platform.saga.orchestrator.event.InventoryReleasedEvent;
import com.platform.saga.orchestrator.event.InventoryReservationFailedEvent;
import com.platform.saga.orchestrator.event.InventoryReservedEvent;
import com.platform.saga.orchestrator.event.OrderCreatedEvent;
import com.platform.saga.orchestrator.event.PaymentProcessedEvent;
import com.platform.saga.orchestrator.event.PaymentProcessingFailedEvent;
import com.platform.saga.orchestrator.model.OrderSaga;
import com.platform.saga.orchestrator.model.SagaStatus;
import com.platform.saga.orchestrator.repository.SagaRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaService {

  private static final BigDecimal UNIT_PRICE = new BigDecimal("10.00");

  @Value("${topics.inventory.reserve}")
  private String topicInventoryReserve;

  @Value("${topics.inventory.release}")
  private String topicInventoryRelease;

  @Value("${topics.payment.process}")
  private String topicPaymentProcess;

  @Value("${topics.order.confirmed}")
  private String topicOrderConfirmed;

  @Value("${topics.order.canceled}")
  private String topicOrderCanceled;

  private final SagaRepository sagaRepository;
  private final KafkaProducerService kafkaProducerService;

  public void handleOrderCreated(OrderCreatedEvent event) {
    OrderSaga saga = new OrderSaga();
    saga.setOrderId(event.getOrderId());
    saga.setProductId(event.getProductId());
    saga.setQuantity(event.getQuantity());
    saga.setStatus(SagaStatus.STARTED);
    sagaRepository.save(saga);
    log.info("Saga started for orderId={}", event.getOrderId());

    saga.setStatus(SagaStatus.INVENTORY_PENDING);
    sagaRepository.save(saga);

    kafkaProducerService.send(
        topicInventoryReserve,
        event.getOrderId(),
        new ReserveInventoryCommand(event.getOrderId(), event.getProductId(), event.getQuantity()));
    log.info("Published ReserveInventoryCommand for orderId={}", event.getOrderId());
  }

  public void handleInventoryReserved(InventoryReservedEvent event) {
    OrderSaga saga = findSaga(event.getOrderId(), "order.inventory.reserved");
    if (saga == null) return;

    saga.setStatus(SagaStatus.INVENTORY_CONFIRMED);
    sagaRepository.save(saga);
    log.info("Inventory confirmed for orderId={}", event.getOrderId());

    saga.setStatus(SagaStatus.PAYMENT_PENDING);
    sagaRepository.save(saga);

    BigDecimal amount = UNIT_PRICE.multiply(BigDecimal.valueOf(event.getQuantity()));
    kafkaProducerService.send(
        topicPaymentProcess,
        event.getOrderId(),
        new ProcessPaymentCommand(
            event.getOrderId(), event.getProductId(), event.getQuantity(), amount));
    log.info(
        "Published ProcessPaymentCommand for orderId={}, amount={}", event.getOrderId(), amount);
  }

  public void handleInventoryReservationFailed(InventoryReservationFailedEvent event) {
    OrderSaga saga = findSaga(event.getOrderId(), "order.inventory.failed");
    if (saga == null) return;

    saga.setStatus(SagaStatus.INVENTORY_FAILED);
    sagaRepository.save(saga);
    log.warn("Inventory reservation failed for orderId={}", event.getOrderId());

    cancelSaga(saga, event.getOrderId(), event.getReason());
  }

  public void handlePaymentProcessed(PaymentProcessedEvent event) {
    OrderSaga saga = findSaga(event.getOrderId(), "order.payment.processed");
    if (saga == null) return;

    saga.setStatus(SagaStatus.PAYMENT_CONFIRMED);
    sagaRepository.save(saga);
    log.info("Payment confirmed for orderId={}", event.getOrderId());

    saga.setStatus(SagaStatus.COMPLETED);
    sagaRepository.save(saga);

    kafkaProducerService.send(
        topicOrderConfirmed,
        event.getOrderId(),
        new OrderConfirmedCommand(event.getOrderId(), event.getProductId()));
    log.info("Saga completed, published OrderConfirmedCommand for orderId={}", event.getOrderId());
  }

  public void handlePaymentFailed(PaymentProcessingFailedEvent event) {
    OrderSaga saga = findSaga(event.getOrderId(), "order.payment.failed");
    if (saga == null) return;

    saga.setStatus(SagaStatus.PAYMENT_FAILED);
    saga.setCancellationReason(event.getReason());
    sagaRepository.save(saga);
    log.warn("Payment failed for orderId={}, reason={}", event.getOrderId(), event.getReason());

    saga.setStatus(SagaStatus.COMPENSATING);
    sagaRepository.save(saga);

    kafkaProducerService.send(
        topicInventoryRelease,
        event.getOrderId(),
        new ReleaseInventoryCommand(event.getOrderId(), saga.getProductId(), saga.getQuantity()));
    log.info("Published ReleaseInventoryCommand for orderId={}", event.getOrderId());
  }

  public void handleInventoryReleased(InventoryReleasedEvent event) {
    OrderSaga saga = findSaga(event.getOrderId(), "order.inventory.released");
    if (saga == null) return;

    log.info("Inventory released for orderId={}, proceeding to cancel order", event.getOrderId());
    cancelSaga(saga, event.getOrderId(), saga.getCancellationReason());
  }

  public void handleInventoryReleaseFailed(InventoryReleaseFailedEvent event) {
    OrderSaga saga = findSaga(event.getOrderId(), "order.inventory.release.failed");
    if (saga == null) return;

    log.warn(
        "Inventory release failed for orderId={}, reason={} — stock may be inconsistent, manual intervention required",
        event.getOrderId(),
        event.getReason());

    cancelSaga(saga, event.getOrderId(), saga.getCancellationReason());
  }

  private void cancelSaga(OrderSaga saga, String orderId, String reason) {
    saga.setStatus(SagaStatus.CANCELLED);
    sagaRepository.save(saga);

    kafkaProducerService.send(
        topicOrderCanceled, orderId, new OrderCanceledCommand(orderId, reason));
    log.info("Saga cancelled, published OrderCanceledCommand for orderId={}", orderId);
  }

  private OrderSaga findSaga(String orderId, String topic) {
    Optional<OrderSaga> saga = sagaRepository.findByOrderId(orderId);
    if (saga.isEmpty()) {
      log.warn("No saga found for orderId={} on topic={}", orderId, topic);
      return null;
    }
    return saga.get();
  }
}
