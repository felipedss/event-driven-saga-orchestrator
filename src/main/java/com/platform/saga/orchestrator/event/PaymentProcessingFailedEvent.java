package com.platform.saga.orchestrator.event;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaymentProcessingFailedEvent {
  private String orderId;
  private String reason;
}
