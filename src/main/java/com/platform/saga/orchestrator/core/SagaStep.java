package com.platform.saga.orchestrator.core;

public interface SagaStep {
  String getName();

  String execute(SagaContext sagaContext);

  String compensate(SagaContext sagaContext);
}
