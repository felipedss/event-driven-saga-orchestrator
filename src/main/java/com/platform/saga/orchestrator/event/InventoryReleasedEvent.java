package com.platform.saga.orchestrator.event;

import lombok.Data;

@Data
public class InventoryReleasedEvent {
  private String orderId;
  private String productId;
}
