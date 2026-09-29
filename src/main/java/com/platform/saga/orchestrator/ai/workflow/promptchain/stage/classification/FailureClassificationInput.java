package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;

/**
 * {@code sagaStatus} is the real, persisted {@code SagaStatus} — sourced directly from the
 * deterministic {@code SagaSnapshot}, not from {@code executionAnalysis.currentState()}. Even
 * though {@link
 * com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisStage}
 * now rejects a response that alters {@code currentState}, this stage's own validation does not
 * depend on that guard holding — it always validates against the actual system state, never against
 * a field the model produced.
 */
public record FailureClassificationInput(
    ExecutionAnalysisOutput executionAnalysis, String sagaStatus, String cancellationReason) {}
