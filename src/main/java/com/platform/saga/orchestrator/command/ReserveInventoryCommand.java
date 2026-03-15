package com.platform.saga.orchestrator.command;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReserveInventoryCommand {
  private String orderId;
  private String productId;
  private int quantity;
}
