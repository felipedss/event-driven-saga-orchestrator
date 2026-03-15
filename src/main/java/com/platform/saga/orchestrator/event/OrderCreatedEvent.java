package com.platform.saga.orchestrator.event;

import lombok.Data;

@Data
public class OrderCreatedEvent {
  private String orderId;
  private String productId;
  private int quantity;
}
