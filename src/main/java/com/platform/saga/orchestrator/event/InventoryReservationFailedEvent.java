package com.platform.saga.orchestrator.event;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InventoryReservationFailedEvent {
  private String orderId;
  private String reason;
}
