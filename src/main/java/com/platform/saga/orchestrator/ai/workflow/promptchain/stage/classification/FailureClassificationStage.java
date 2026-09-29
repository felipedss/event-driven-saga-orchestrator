package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

import com.platform.saga.orchestrator.ai.provider.AiModelClient;
import com.platform.saga.orchestrator.ai.provider.AiModelException;
import com.platform.saga.orchestrator.ai.provider.AiModelResponse;
import com.platform.saga.orchestrator.ai.provider.AiRequest;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.PromptChainStage;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageName;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageTelemetry;
import com.platform.saga.orchestrator.model.SagaStatus;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailureClassificationStage
    implements PromptChainStage<FailureClassificationInput, FailureClassificationOutput> {

  private static final String SYSTEM_PROMPT =
      """
      You are classifying the failure/execution state of a distributed saga (an order-fulfillment
      workflow), given the prior stage's Execution Analysis and the saga's cancellation reason if
      any.

      A saga in a "*_PENDING" state is waiting on an external service reply — it is IN_PROGRESS,
      not failed. A saga that is compensating (releasing inventory, refunding, or similar undo
      actions) is COMPENSATING — still executing, not TERMINAL_FAILURE — because the undo action
      has not finished. Only classify TERMINAL_FAILURE when the saga has actually reached a
      resolved failure state with no pending undo action. Use UNKNOWN when the evidence given
      does not clearly support any other value — never guess.
      """;

  /**
   * Mirrors the plan's "Mapping SagaStatus → ExecutionAssessment" table, derived by reading {@code
   * SagaService} directly. {@code UNKNOWN} is always additionally allowed: it is the fallback for
   * genuinely ambiguous input, never itself a validation failure.
   */
  private static final Map<SagaStatus, Set<ExecutionAssessment>> ALLOWED_ASSESSMENTS =
      Map.ofEntries(
          Map.entry(SagaStatus.STARTED, Set.of(ExecutionAssessment.IN_PROGRESS)),
          Map.entry(SagaStatus.INVENTORY_PENDING, Set.of(ExecutionAssessment.IN_PROGRESS)),
          Map.entry(SagaStatus.INVENTORY_CONFIRMED, Set.of(ExecutionAssessment.IN_PROGRESS)),
          Map.entry(SagaStatus.INVENTORY_FAILED, Set.of(ExecutionAssessment.TERMINAL_FAILURE)),
          Map.entry(SagaStatus.PAYMENT_PENDING, Set.of(ExecutionAssessment.IN_PROGRESS)),
          Map.entry(SagaStatus.PAYMENT_CONFIRMED, Set.of(ExecutionAssessment.IN_PROGRESS)),
          Map.entry(SagaStatus.PAYMENT_FAILED, Set.of(ExecutionAssessment.COMPENSATING)),
          Map.entry(SagaStatus.COMPENSATING, Set.of(ExecutionAssessment.COMPENSATING)),
          Map.entry(SagaStatus.COMPLETED, Set.of(ExecutionAssessment.TERMINAL_SUCCESS)),
          Map.entry(SagaStatus.CANCELLED, Set.of(ExecutionAssessment.TERMINAL_FAILURE)));

  private final AiModelClient aiModelClient;

  @Override
  public StageResult<FailureClassificationOutput> execute(
      FailureClassificationInput input, UUID analysisId) {
    AiModelResponse<FailureClassificationOutput> response;
    try {
      response =
          aiModelClient.generate(
              new AiRequest(
                  SYSTEM_PROMPT, buildUserPrompt(input), FailureClassificationOutput.JSON_SCHEMA),
              FailureClassificationOutput.class);
    } catch (AiModelException e) {
      log.warn("[{}] Failure Classification provider call failed: {}", analysisId, e.getMessage());
      return StageResult.failure(
          StageName.FAILURE_CLASSIFICATION, classifyProviderFailure(e), e.getMessage(), null);
    } catch (Exception e) {
      log.error("[{}] Failure Classification stage failed unexpectedly", analysisId, e);
      return StageResult.failure(
          StageName.FAILURE_CLASSIFICATION, StageStatus.UNEXPECTED_ERROR, e.getMessage(), null);
    }

    StageTelemetry telemetry = telemetryFrom(response);
    FailureClassificationOutput output = response.output();
    String violation = validate(input, output);
    if (violation != null) {
      log.warn("[{}] Failure Classification output failed validation: {}", analysisId, violation);
      return StageResult.failure(
          StageName.FAILURE_CLASSIFICATION, StageStatus.VALIDATION_FAILED, violation, telemetry);
    }

    log.info(
        "[{}] Failure Classification succeeded, model={}, latencyMs={}",
        analysisId,
        telemetry.model(),
        telemetry.latencyMs());
    return StageResult.success(StageName.FAILURE_CLASSIFICATION, output, telemetry);
  }

  private String validate(FailureClassificationInput input, FailureClassificationOutput output) {
    if (output.category() == null) {
      return "category is missing";
    }
    if (output.executionAssessment() == null) {
      return "executionAssessment is missing";
    }
    if (output.reasoning() == null || output.reasoning().isBlank()) {
      return "reasoning is missing";
    }
    if (output.confidence() < 0.0 || output.confidence() > 1.0) {
      return "confidence must be between 0.0 and 1.0, got " + output.confidence();
    }

    // Deliberately sourced from input.sagaStatus() — the real, persisted status — never from
    // input.executionAnalysis().currentState(), which is LLM-produced output and must not be
    // trusted as the ground truth for validating another LLM output against.
    SagaStatus status = parseStatus(input.sagaStatus());
    if (status == null) {
      // Unrecognized status string — nothing to validate against; UNKNOWN-style leniency.
      return null;
    }

    Set<ExecutionAssessment> allowed = ALLOWED_ASSESSMENTS.get(status);
    if (allowed != null
        && !allowed.contains(output.executionAssessment())
        && output.executionAssessment() != ExecutionAssessment.UNKNOWN) {
      return "executionAssessment "
          + output.executionAssessment()
          + " is not valid for status "
          + status
          + " (expected one of "
          + allowed
          + " or UNKNOWN)";
    }
    return null;
  }

  private SagaStatus parseStatus(String currentState) {
    if (currentState == null) {
      return null;
    }
    try {
      return SagaStatus.valueOf(currentState.trim());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private String buildUserPrompt(FailureClassificationInput input) {
    return """
        Saga status (deterministic ground truth): %s
        Execution Analysis reached phases: %s
        Execution Analysis narrative summary: %s
        Cancellation reason: %s
        """
        .formatted(
            input.sagaStatus(),
            input.executionAnalysis().reachedPhases(),
            input.executionAnalysis().narrativeSummary(),
            input.cancellationReason() == null ? "none" : input.cancellationReason());
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
