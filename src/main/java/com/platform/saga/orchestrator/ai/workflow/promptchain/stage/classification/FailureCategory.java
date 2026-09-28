package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

public enum FailureCategory {
  TRANSIENT_INFRASTRUCTURE_FAILURE,
  PAYMENT_PROVIDER_FAILURE,
  INVENTORY_FAILURE,
  TIMEOUT,
  INVALID_STATE,
  COMPENSATION_FAILURE,
  UNKNOWN
}
