package com.platform.saga.orchestrator.command;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessPaymentCommand {
  private String orderId;
  private String productId;
  private int quantity;
  private BigDecimal amount;
}
