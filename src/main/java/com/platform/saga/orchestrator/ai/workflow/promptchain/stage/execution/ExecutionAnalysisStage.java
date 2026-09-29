package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution;

import com.platform.saga.orchestrator.ai.model.SagaSnapshot;
import com.platform.saga.orchestrator.ai.provider.AiModelClient;
import com.platform.saga.orchestrator.ai.provider.AiModelException;
import com.platform.saga.orchestrator.ai.provider.AiModelResponse;
import com.platform.saga.orchestrator.ai.provider.AiRequest;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.PromptChainStage;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageName;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageTelemetry;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionAnalysisStage
    implements PromptChainStage<ExecutionAnalysisInput, ExecutionAnalysisOutput> {

  private static final String SYSTEM_PROMPT =
      """
      You are analyzing the execution state of a distributed saga (an order-fulfillment workflow).
      You are given only the saga's current persisted status, its deterministically-known reached
      phases, and its cancellation reason if any.

      You do NOT have access to: a step-by-step event history, DLQ (dead-letter queue) contents,
      or the actual number of retry attempts. These are not partially available or inferable —
      they simply do not exist as data sources in this system. Never claim otherwise in
      dataCompleteness, no matter how confident the rest of the analysis is.

      Report only what the given data supports. Do not invent prior events, timestamps, or
      outcomes you were not given.
      """;

  private final AiModelClient aiModelClient;

  @Override
  public StageResult<ExecutionAnalysisOutput> execute(
      ExecutionAnalysisInput input, UUID analysisId) {
    AiModelResponse<ExecutionAnalysisOutput> response;
    try {
      response =
          aiModelClient.generate(
              new AiRequest(
                  SYSTEM_PROMPT,
                  buildUserPrompt(input.saga()),
                  ExecutionAnalysisOutput.JSON_SCHEMA),
              ExecutionAnalysisOutput.class);
    } catch (AiModelException e) {
      log.warn("[{}] Execution Analysis provider call failed: {}", analysisId, e.getMessage());
      return StageResult.failure(
          StageName.EXECUTION_ANALYSIS, classifyProviderFailure(e), e.getMessage(), null);
    } catch (Exception e) {
      log.error("[{}] Execution Analysis stage failed unexpectedly", analysisId, e);
      return StageResult.failure(
          StageName.EXECUTION_ANALYSIS, StageStatus.UNEXPECTED_ERROR, e.getMessage(), null);
    }

    StageTelemetry telemetry = telemetryFrom(response);
    ExecutionAnalysisOutput output = response.output();
    String violation = validate(input, output);
    if (violation != null) {
      log.warn("[{}] Execution Analysis output failed validation: {}", analysisId, violation);
      return StageResult.failure(
          StageName.EXECUTION_ANALYSIS, StageStatus.VALIDATION_FAILED, violation, telemetry);
    }

    log.info(
        "[{}] Execution Analysis succeeded, model={}, latencyMs={}",
        analysisId,
        telemetry.model(),
        telemetry.latencyMs());
    return StageResult.success(StageName.EXECUTION_ANALYSIS, output, telemetry);
  }

  /**
   * The fabrication guard. Two independent things are checked:
   *
   * <ol>
   *   <li><b>Determinism:</b> {@code currentState} and {@code reachedPhases} are not the model's
   *       opinion — they are facts already computed in Java by {@link SagaSnapshot}. The model is
   *       only allowed to echo them back (it may add narrative framing in {@code narrativeSummary},
   *       never redefine the facts themselves). Any deviation means the model substituted its own
   *       belief about the saga's state for the deterministic ground truth, which {@link
   *       FailureClassificationStage} would otherwise trust downstream.
   *   <li><b>Capability claims:</b> {@code dataCompleteness} must not claim data sources this
   *       milestone lacks.
   * </ol>
   */
  private String validate(ExecutionAnalysisInput input, ExecutionAnalysisOutput output) {
    SagaSnapshot saga = input.saga();
    if (!Objects.equals(output.currentState(), saga.status())) {
      return "currentState was changed by the model: expected the deterministic saga status '"
          + saga.status()
          + "' but got '"
          + output.currentState()
          + "'";
    }
    if (!Objects.equals(output.reachedPhases(), saga.reachedPhases())) {
      return "reachedPhases was changed by the model: expected the deterministic phases "
          + saga.reachedPhases()
          + " but got "
          + output.reachedPhases();
    }

    DataCompleteness completeness = output.dataCompleteness();
    if (completeness == null) {
      return "dataCompleteness is missing";
    }
    if (!completeness.onlyCurrentStatusSnapshotAvailable()) {
      return "onlyCurrentStatusSnapshotAvailable must be true — this milestone has no other data"
          + " source";
    }
    if (completeness.stepByStepEventHistoryAvailable()) {
      return "stepByStepEventHistoryAvailable must be false — no saga history table exists";
    }
    if (completeness.dlqCheckAvailable()) {
      return "dlqCheckAvailable must be false — DLQ lookup is not implemented in this milestone";
    }
    if (completeness.retryCountKnown()) {
      return "retryCountKnown must be false — actual per-saga retry attempts are not tracked";
    }
    return null;
  }

  private String buildUserPrompt(SagaSnapshot saga) {
    return """
        Saga ID: %s
        Order ID: %s
        Current status: %s
        Deterministically reached phases: %s
        Cancellation reason: %s
        Created at: %s
        Updated at: %s
        """
        .formatted(
            saga.sagaId(),
            saga.orderId(),
            saga.status(),
            saga.reachedPhases(),
            saga.cancellationReason() == null ? "none" : saga.cancellationReason(),
            saga.createdAt(),
            saga.updatedAt());
  }

  private StageStatus classifyProviderFailure(AiModelException e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof SocketTimeoutException) {
        return StageStatus.TIMEOUT;
      }
    }
    return StageStatus.PROVIDER_ERROR;
  }

  private StageTelemetry telemetryFrom(AiModelResponse<?> response) {
    return new StageTelemetry(
        response.model(),
        response.provider(),
        response.latencyMs(),
        response.promptTokens(),
        response.completionTokens());
  }
}
