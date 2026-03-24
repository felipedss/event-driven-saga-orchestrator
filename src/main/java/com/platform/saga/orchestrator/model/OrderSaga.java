package com.platform.saga.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Data
public class OrderSaga {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID sagaId;

  @Column(unique = true, nullable = false)
  private String orderId;

  private String productId;
  private int quantity;
  private String cancellationReason;

  @Enumerated(EnumType.STRING)
  private SagaStatus status;

  private Instant createdAt;
  private Instant updatedAt;
}
