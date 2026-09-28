package com.platform.saga.orchestrator.ai.exception;

import java.util.UUID;

public class SagaNotFoundException extends RuntimeException {

  public SagaNotFoundException(UUID sagaId) {
    super("Saga not found: " + sagaId);
  }
}
