package com.platform.saga.orchestrator.event;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InventoryReleaseFailedEvent {
  private String orderId;
  private String productId;
  private String reason;
}
