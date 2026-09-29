package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.platform.saga.orchestrator.ai.provider.AiModelClient;
import com.platform.saga.orchestrator.ai.provider.AiModelException;
import com.platform.saga.orchestrator.ai.provider.AiModelResponse;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.DataCompleteness;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailureClassificationStageTest {

  @Mock private AiModelClient aiModelClient;
  @InjectMocks private FailureClassificationStage stage;

  private static final UUID ANALYSIS_ID = UUID.randomUUID();

  // ── the SagaStatus → ExecutionAssessment mapping table (plan 2.3) ──────────────────────────

  @ParameterizedTest
  @CsvSource({
    "STARTED, IN_PROGRESS",
    "INVENTORY_PENDING, IN_PROGRESS",
    "INVENTORY_CONFIRMED, IN_PROGRESS",
    "PAYMENT_PENDING, IN_PROGRESS",
    "PAYMENT_CONFIRMED, IN_PROGRESS",
    "PAYMENT_FAILED, COMPENSATING",
    "COMPENSATING, COMPENSATING",
    "COMPLETED, TERMINAL_SUCCESS",
    "CANCELLED, TERMINAL_FAILURE",
    "INVENTORY_FAILED, TERMINAL_FAILURE",
  })
  void execute_succeeds_forEachValidStatusToAssessmentMapping(String status, String assessment) {
    stubResponse(classification(FailureCategory.UNKNOWN, ExecutionAssessment.valueOf(assessment)));

    var result = stage.execute(classificationInput(status), ANALYSIS_ID);

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void execute_succeeds_whenStatusIsUnrecognized() {
    // A status this stage doesn't recognize (e.g. a future SagaStatus it hasn't been updated
    // for) has no entry in the mapping — nothing to validate against, so it's lenient.
    stubResponse(classification(FailureCategory.UNKNOWN, ExecutionAssessment.UNKNOWN));

    var result = stage.execute(classificationInput("SOME_FUTURE_STATUS"), ANALYSIS_ID);

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void execute_returnsValidationFailed_whenPendingStatusClassifiedAsTerminal() {
    stubResponse(
        classification(FailureCategory.INVENTORY_FAILURE, ExecutionAssessment.TERMINAL_FAILURE));

    var result = stage.execute(classificationInput("INVENTORY_PENDING"), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsValidationFailed_whenPaymentFailedClassifiedAsTerminalFailure() {
    // The specific regression this revision fixes: PAYMENT_FAILED always enters COMPENSATING in
    // the same SagaService call — it must never be classified as a resolved TERMINAL_FAILURE.
    stubResponse(
        classification(
            FailureCategory.PAYMENT_PROVIDER_FAILURE, ExecutionAssessment.TERMINAL_FAILURE));

    var result = stage.execute(classificationInput("PAYMENT_FAILED"), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsValidationFailed_whenReasoningIsMissing() {
    stubResponse(
        new FailureClassificationOutput(
            FailureCategory.UNKNOWN, ExecutionAssessment.IN_PROGRESS, 0.5, null));

    var result = stage.execute(classificationInput("STARTED"), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsValidationFailed_whenConfidenceOutOfRange() {
    stubResponse(
        new FailureClassificationOutput(
            FailureCategory.UNKNOWN, ExecutionAssessment.IN_PROGRESS, 1.5, "reasoning"));

    var result = stage.execute(classificationInput("STARTED"), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_validatesAgainstRealSagaStatus_notExecutionAnalysisCurrentState() {
    // executionAnalysis.currentState() is deliberately stale/wrong (as if Stage 1's own
    // determinism guard had somehow been bypassed); sagaStatus is the real, persisted value and
    // must be what this stage's validation actually uses.
    FailureClassificationInput input = classificationInput("PAYMENT_FAILED", "COMPLETED");
    // Correct per the REAL status (PAYMENT_FAILED -> COMPENSATING), wrong per the stale one
    // (COMPLETED -> TERMINAL_SUCCESS only) — if the stage validated against the stale field this
    // would fail.
    stubResponse(classification(FailureCategory.UNKNOWN, ExecutionAssessment.COMPENSATING));

    var result = stage.execute(input, ANALYSIS_ID);

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void execute_returnsProviderError_whenProviderThrowsAiModelException() {
    when(aiModelClient.generate(any(), eq(FailureClassificationOutput.class)))
        .thenThrow(new AiModelException("boom"));

    var result = stage.execute(classificationInput("STARTED"), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.PROVIDER_ERROR);
  }

  @Test
  void execute_returnsTimeout_whenProviderThrowsSocketTimeoutException() {
    when(aiModelClient.generate(any(), eq(FailureClassificationOutput.class)))
        .thenThrow(new AiModelException("timed out", new SocketTimeoutException("read timed out")));

    var result = stage.execute(classificationInput("STARTED"), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.TIMEOUT);
  }

  private void stubResponse(FailureClassificationOutput output) {
    when(aiModelClient.generate(any(), eq(FailureClassificationOutput.class)))
        .thenReturn(new AiModelResponse<>(output, "{}", 100, 50, 200L, "gpt-4o-mini", "openai"));
  }

  private FailureClassificationOutput classification(
      FailureCategory category, ExecutionAssessment assessment) {
    return new FailureClassificationOutput(category, assessment, 0.8, "reasoning");
  }

  private FailureClassificationInput classificationInput(String sagaStatus) {
    return classificationInput(sagaStatus, sagaStatus);
  }

  private FailureClassificationInput classificationInput(
      String sagaStatus, String executionAnalysisCurrentState) {
    ExecutionAnalysisOutput executionAnalysis =
        new ExecutionAnalysisOutput(
            executionAnalysisCurrentState,
            List.of(executionAnalysisCurrentState),
            "summary",
            new DataCompleteness(true, false, false, false, List.of("limitation")));
    return new FailureClassificationInput(executionAnalysis, sagaStatus, null);
  }
}
