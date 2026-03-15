package com.platform.saga.orchestrator.event;

import lombok.Data;

@Data
public class InventoryReservedEvent {
  private String orderId;
  private String productId;
  private int quantity;
}
