package com.platform.saga.orchestrator.ai.model;

import com.platform.saga.orchestrator.model.OrderSaga;
import com.platform.saga.orchestrator.model.SagaStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Provider-/workflow-neutral view of an {@link OrderSaga}, decoupling the {@code ai} package from
 * the JPA entity.
 *
 * <p>{@code reachedPhases} is computed deterministically from {@link SagaStatus} — not by the LLM —
 * because the orchestrator's state machine ({@code SagaService}) encodes a known progression:
 * reaching a given status guarantees certain earlier statuses were already passed through (e.g.
 * reaching {@code PAYMENT_PENDING} implies inventory was already confirmed). The LLM is never asked
 * to guess prior events, timestamps, retries, or outcomes it wasn't given.
 *
 * <p>{@code CANCELLED} is reached from two structurally different paths in {@code SagaService}
 * (straight from {@code INVENTORY_FAILED}, with no compensation step; or from {@code COMPENSATING}
 * after a payment failure) — so only the phases common to <em>both</em> paths ({@code STARTED},
 * {@code INVENTORY_PENDING}) can be stated as guaranteed. Anything not common to every path that
 * reaches a given status is deliberately left out, rather than guessed.
 */
public record SagaSnapshot(
    UUID sagaId,
    String orderId,
    String productId,
    int quantity,
    String status,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt,
    List<String> reachedPhases) {

  public static SagaSnapshot from(OrderSaga saga) {
    return new SagaSnapshot(
        saga.getSagaId(),
        saga.getOrderId(),
        saga.getProductId(),
        saga.getQuantity(),
        saga.getStatus() != null ? saga.getStatus().name() : null,
        saga.getCancellationReason(),
        saga.getCreatedAt(),
        saga.getUpdatedAt(),
        reachedPhasesFor(saga.getStatus()));
  }

  private static List<String> reachedPhasesFor(SagaStatus status) {
    if (status == null) {
      return List.of();
    }
    return switch (status) {
      case STARTED -> names(SagaStatus.STARTED);
      case INVENTORY_PENDING -> names(SagaStatus.STARTED, SagaStatus.INVENTORY_PENDING);
      case INVENTORY_CONFIRMED ->
          names(SagaStatus.STARTED, SagaStatus.INVENTORY_PENDING, SagaStatus.INVENTORY_CONFIRMED);
      case INVENTORY_FAILED ->
          names(SagaStatus.STARTED, SagaStatus.INVENTORY_PENDING, SagaStatus.INVENTORY_FAILED);
      case PAYMENT_PENDING ->
          names(
              SagaStatus.STARTED,
              SagaStatus.INVENTORY_PENDING,
              SagaStatus.INVENTORY_CONFIRMED,
              SagaStatus.PAYMENT_PENDING);
      case PAYMENT_CONFIRMED ->
          names(
              SagaStatus.STARTED,
              SagaStatus.INVENTORY_PENDING,
              SagaStatus.INVENTORY_CONFIRMED,
              SagaStatus.PAYMENT_PENDING,
              SagaStatus.PAYMENT_CONFIRMED);
      case PAYMENT_FAILED ->
          names(
              SagaStatus.STARTED,
              SagaStatus.INVENTORY_PENDING,
              SagaStatus.INVENTORY_CONFIRMED,
              SagaStatus.PAYMENT_PENDING,
              SagaStatus.PAYMENT_FAILED);
      case COMPENSATING ->
          names(
              SagaStatus.STARTED,
              SagaStatus.INVENTORY_PENDING,
              SagaStatus.INVENTORY_CONFIRMED,
              SagaStatus.PAYMENT_PENDING,
              SagaStatus.PAYMENT_FAILED,
              SagaStatus.COMPENSATING);
      case COMPLETED ->
          names(
              SagaStatus.STARTED,
              SagaStatus.INVENTORY_PENDING,
              SagaStatus.INVENTORY_CONFIRMED,
              SagaStatus.PAYMENT_PENDING,
              SagaStatus.PAYMENT_CONFIRMED,
              SagaStatus.COMPLETED);
      // CANCELLED merges two paths (post-INVENTORY_FAILED with no compensation, or
      // post-COMPENSATING after a payment failure) — only the phases common to both are
      // guaranteed; see class-level Javadoc.
      case CANCELLED ->
          names(SagaStatus.STARTED, SagaStatus.INVENTORY_PENDING, SagaStatus.CANCELLED);
    };
  }

  private static List<String> names(SagaStatus... statuses) {
    return Arrays.stream(statuses).map(Enum::name).toList();
  }
}
