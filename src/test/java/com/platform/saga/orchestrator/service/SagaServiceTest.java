package com.platform.saga.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SagaServiceTest {

  @Mock private SagaRepository sagaRepository;
  @Mock private KafkaProducerService kafkaProducerService;
  @InjectMocks private SagaService sagaService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(sagaService, "topicInventoryReserve", "order.inventory.reserve");
    ReflectionTestUtils.setField(sagaService, "topicInventoryRelease", "order.inventory.release");
    ReflectionTestUtils.setField(sagaService, "topicPaymentProcess", "order.payment.process");
    ReflectionTestUtils.setField(sagaService, "topicOrderConfirmed", "order.confirmed");
    ReflectionTestUtils.setField(sagaService, "topicOrderCanceled", "order.canceled");
  }

  // ── handleOrderCreated ──────────────────────────────────────────────────────

  @Test
  void handleOrderCreated_savesSagaAndPublishesReserveInventoryCommand() {
    OrderCreatedEvent event = new OrderCreatedEvent();
    event.setOrderId("order-1");
    event.setProductId("product-A");
    event.setQuantity(2);
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleOrderCreated(event);

    verify(sagaRepository, times(2)).save(any(OrderSaga.class));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService)
        .send(eq("order.inventory.reserve"), eq("order-1"), captor.capture());
    ReserveInventoryCommand cmd = (ReserveInventoryCommand) captor.getValue();
    assertThat(cmd.getOrderId()).isEqualTo("order-1");
    assertThat(cmd.getProductId()).isEqualTo("product-A");
  }

  @Test
  void handleOrderCreated_storesProductIdAndQuantityInSaga() {
    OrderCreatedEvent event = new OrderCreatedEvent();
    event.setOrderId("order-1");
    event.setProductId("product-A");
    event.setQuantity(5);
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleOrderCreated(event);

    OrderSaga firstSaved = sagaCaptor.getAllValues().get(0);
    assertThat(firstSaved.getProductId()).isEqualTo("product-A");
    assertThat(firstSaved.getQuantity()).isEqualTo(5);
  }

  @Test
  void handleOrderCreated_sagaEndsWithInventoryPendingStatus() {
    OrderCreatedEvent event = new OrderCreatedEvent();
    event.setOrderId("order-2");
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleOrderCreated(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.INVENTORY_PENDING);
  }

  // ── handleInventoryReserved ─────────────────────────────────────────────────

  @Test
  void handleInventoryReserved_publishesProcessPaymentCommand() {
    InventoryReservedEvent event = new InventoryReservedEvent();
    event.setOrderId("order-1");
    event.setProductId("product-A");
    event.setQuantity(3);

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.INVENTORY_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReserved(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService).send(eq("order.payment.process"), eq("order-1"), captor.capture());
    ProcessPaymentCommand cmd = (ProcessPaymentCommand) captor.getValue();
    assertThat(cmd.getOrderId()).isEqualTo("order-1");
    assertThat(cmd.getProductId()).isEqualTo("product-A");
    assertThat(cmd.getQuantity()).isEqualTo(3);
    assertThat(cmd.getAmount()).isEqualByComparingTo("30.00");
  }

  @Test
  void handleInventoryReserved_sagaEndsWithPaymentPendingStatus() {
    InventoryReservedEvent event = new InventoryReservedEvent();
    event.setOrderId("order-1");
    event.setQuantity(1);

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.INVENTORY_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReserved(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.PAYMENT_PENDING);
  }

  // ── handleInventoryReservationFailed ───────────────────────────────────────

  @Test
  void handleInventoryReservationFailed_cancelsSagaAndPublishesOrderCancelledEvent() {
    InventoryReservationFailedEvent event = new InventoryReservationFailedEvent();
    event.setOrderId("order-1");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.INVENTORY_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReservationFailed(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService).send(eq("order.canceled"), eq("order-1"), captor.capture());
    assertThat(captor.getValue()).isInstanceOf(OrderCanceledCommand.class);
  }

  @Test
  void handleInventoryReservationFailed_sagaEndsWithCancelledStatus() {
    InventoryReservationFailedEvent event = new InventoryReservationFailedEvent();
    event.setOrderId("order-1");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.INVENTORY_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReservationFailed(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.CANCELLED);
  }

  // ── handlePaymentProcessed ──────────────────────────────────────────────────

  @Test
  void handlePaymentProcessed_completesSagaAndPublishesOrderConfirmedCommand() {
    PaymentProcessedEvent event = new PaymentProcessedEvent();
    event.setOrderId("order-1");
    event.setProductId("product-A");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.PAYMENT_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handlePaymentProcessed(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService).send(eq("order.confirmed"), eq("order-1"), captor.capture());
    assertThat(captor.getValue()).isInstanceOf(OrderConfirmedCommand.class);
  }

  @Test
  void handlePaymentProcessed_sagaEndsWithCompletedStatus() {
    PaymentProcessedEvent event = new PaymentProcessedEvent();
    event.setOrderId("order-1");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.PAYMENT_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handlePaymentProcessed(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.COMPLETED);
  }

  // ── handlePaymentFailed (compensation) ─────────────────────────────────────

  @Test
  void handlePaymentFailed_publishesReleaseInventoryCommand() {
    PaymentProcessingFailedEvent event = new PaymentProcessingFailedEvent();
    event.setOrderId("order-1");
    event.setReason("Insufficient funds");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.PAYMENT_PENDING);
    saga.setProductId("product-A");
    saga.setQuantity(3);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handlePaymentFailed(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService)
        .send(eq("order.inventory.release"), eq("order-1"), captor.capture());
    ReleaseInventoryCommand cmd = (ReleaseInventoryCommand) captor.getValue();
    assertThat(cmd.getOrderId()).isEqualTo("order-1");
    assertThat(cmd.getProductId()).isEqualTo("product-A");
    assertThat(cmd.getQuantity()).isEqualTo(3);
  }

  @Test
  void handlePaymentFailed_sagaEndsWithCompensatingStatus() {
    PaymentProcessingFailedEvent event = new PaymentProcessingFailedEvent();
    event.setOrderId("order-1");
    event.setReason("Insufficient funds");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.PAYMENT_PENDING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handlePaymentFailed(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.COMPENSATING);
  }

  // ── handleInventoryReleased ─────────────────────────────────────────────────

  @Test
  void handleInventoryReleased_cancelsSagaAndPublishesOrderCanceledCommand() {
    InventoryReleasedEvent event = new InventoryReleasedEvent();
    event.setOrderId("order-1");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.COMPENSATING);
    saga.setCancellationReason("Insufficient funds");
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReleased(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService).send(eq("order.canceled"), eq("order-1"), captor.capture());
    OrderCanceledCommand cmd = (OrderCanceledCommand) captor.getValue();
    assertThat(cmd.reason()).isEqualTo("Insufficient funds");
  }

  @Test
  void handleInventoryReleased_sagaEndsWithCancelledStatus() {
    InventoryReleasedEvent event = new InventoryReleasedEvent();
    event.setOrderId("order-1");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.COMPENSATING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReleased(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.CANCELLED);
  }

  // ── handleInventoryReleaseFailed ────────────────────────────────────────────

  @Test
  void handleInventoryReleaseFailed_cancelsSagaAndPublishesOrderCanceledCommand() {
    InventoryReleaseFailedEvent event = new InventoryReleaseFailedEvent();
    event.setOrderId("order-1");
    event.setReason("Product not found");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.COMPENSATING);
    saga.setCancellationReason("Insufficient funds");
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    when(sagaRepository.save(any(OrderSaga.class))).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReleaseFailed(event);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(kafkaProducerService).send(eq("order.canceled"), eq("order-1"), captor.capture());
    OrderCanceledCommand cmd = (OrderCanceledCommand) captor.getValue();
    assertThat(cmd.reason()).isEqualTo("Insufficient funds");
  }

  @Test
  void handleInventoryReleaseFailed_sagaEndsWithCancelledStatus() {
    InventoryReleaseFailedEvent event = new InventoryReleaseFailedEvent();
    event.setOrderId("order-1");

    OrderSaga saga = sagaWithStatus("order-1", SagaStatus.COMPENSATING);
    when(sagaRepository.findByOrderId("order-1")).thenReturn(Optional.of(saga));
    ArgumentCaptor<OrderSaga> sagaCaptor = ArgumentCaptor.forClass(OrderSaga.class);
    when(sagaRepository.save(sagaCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

    sagaService.handleInventoryReleaseFailed(event);

    OrderSaga lastSaved = sagaCaptor.getAllValues().get(sagaCaptor.getAllValues().size() - 1);
    assertThat(lastSaved.getStatus()).isEqualTo(SagaStatus.CANCELLED);
  }

  // ── helpers ─────────────────────────────────────────────────────────────────

  private OrderSaga sagaWithStatus(String orderId, SagaStatus status) {
    OrderSaga saga = new OrderSaga();
    saga.setOrderId(orderId);
    saga.setStatus(status);
    return saga;
  }
}
