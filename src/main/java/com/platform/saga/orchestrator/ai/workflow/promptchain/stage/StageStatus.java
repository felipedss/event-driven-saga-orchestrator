package com.platform.saga.orchestrator.ai.workflow.promptchain.stage;

public enum StageStatus {
  SUCCESS,
  VALIDATION_FAILED,
  PROVIDER_ERROR,
  TIMEOUT,
  UNEXPECTED_ERROR
}
