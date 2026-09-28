package com.platform.saga.orchestrator.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.platform.saga.orchestrator.model.OrderSaga;
import com.platform.saga.orchestrator.model.SagaStatus;
import org.junit.jupiter.api.Test;

class SagaSnapshotTest {

  @Test
  void from_paymentPending_includesInventoryConfirmedAsGuaranteedPriorPhase() {
    OrderSaga saga = sagaWithStatus(SagaStatus.PAYMENT_PENDING);

    SagaSnapshot snapshot = SagaSnapshot.from(saga);

    assertThat(snapshot.reachedPhases())
        .containsExactly("STARTED", "INVENTORY_PENDING", "INVENTORY_CONFIRMED", "PAYMENT_PENDING");
  }

  @Test
  void from_compensating_includesPaymentFailedAsGuaranteedPriorPhase() {
    OrderSaga saga = sagaWithStatus(SagaStatus.COMPENSATING);

    SagaSnapshot snapshot = SagaSnapshot.from(saga);

    assertThat(snapshot.reachedPhases())
        .containsExactly(
            "STARTED",
            "INVENTORY_PENDING",
            "INVENTORY_CONFIRMED",
            "PAYMENT_PENDING",
            "PAYMENT_FAILED",
            "COMPENSATING");
  }

  @Test
  void from_cancelled_onlyIncludesPhasesCommonToBothPathsThatReachIt() {
    // CANCELLED is reached either straight from INVENTORY_FAILED (no compensation), or from
    // COMPENSATING after a payment failure — only STARTED and INVENTORY_PENDING are common to
    // both, so only those (plus CANCELLED itself) are guaranteed.
    OrderSaga saga = sagaWithStatus(SagaStatus.CANCELLED);

    SagaSnapshot snapshot = SagaSnapshot.from(saga);

    assertThat(snapshot.reachedPhases())
        .containsExactly("STARTED", "INVENTORY_PENDING", "CANCELLED");
  }

  @Test
  void from_inventoryFailed_doesNotClaimInventoryConfirmed() {
    OrderSaga saga = sagaWithStatus(SagaStatus.INVENTORY_FAILED);

    SagaSnapshot snapshot = SagaSnapshot.from(saga);

    assertThat(snapshot.reachedPhases())
        .containsExactly("STARTED", "INVENTORY_PENDING", "INVENTORY_FAILED");
  }

  @Test
  void from_completed_includesFullHappyPath() {
    OrderSaga saga = sagaWithStatus(SagaStatus.COMPLETED);

    SagaSnapshot snapshot = SagaSnapshot.from(saga);

    assertThat(snapshot.reachedPhases())
        .containsExactly(
            "STARTED",
            "INVENTORY_PENDING",
            "INVENTORY_CONFIRMED",
            "PAYMENT_PENDING",
            "PAYMENT_CONFIRMED",
            "COMPLETED");
  }

  private OrderSaga sagaWithStatus(SagaStatus status) {
    OrderSaga saga = new OrderSaga();
    saga.setOrderId("order-1");
    saga.setStatus(status);
    return saga;
  }
}
