package com.platform.saga.orchestrator.event;

import lombok.Data;

@Data
public class PaymentProcessedEvent {
  private String orderId;
  private String productId;
}
