package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.platform.saga.orchestrator.ai.model.SagaSnapshot;
import com.platform.saga.orchestrator.ai.provider.AiModelClient;
import com.platform.saga.orchestrator.ai.provider.AiModelException;
import com.platform.saga.orchestrator.ai.provider.AiModelResponse;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageName;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionAnalysisStageTest {

  @Mock private AiModelClient aiModelClient;
  @InjectMocks private ExecutionAnalysisStage stage;

  private static final UUID ANALYSIS_ID = UUID.randomUUID();

  @Test
  void execute_returnsSuccess_forWellFormedOutput() {
    ExecutionAnalysisOutput output = honestOutput(List.of("Only current status known"));
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenReturn(response(output));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.stageName()).isEqualTo(StageName.EXECUTION_ANALYSIS);
    assertThat(result.output()).isEqualTo(output);
  }

  @Test
  void execute_returnsValidationFailed_whenClaimingStepByStepHistoryAvailable() {
    ExecutionAnalysisOutput output =
        new ExecutionAnalysisOutput(
            "PAYMENT_PENDING",
            List.of("STARTED"),
            "summary",
            new DataCompleteness(true, true, false, false, List.of()));
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenReturn(response(output));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsValidationFailed_whenClaimingRetryCountKnown() {
    ExecutionAnalysisOutput output =
        new ExecutionAnalysisOutput(
            "PAYMENT_PENDING",
            List.of("STARTED"),
            "summary",
            new DataCompleteness(true, false, false, true, List.of()));
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenReturn(response(output));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsValidationFailed_whenClaimingDlqCheckAvailable() {
    ExecutionAnalysisOutput output =
        new ExecutionAnalysisOutput(
            "PAYMENT_PENDING",
            List.of("STARTED"),
            "summary",
            new DataCompleteness(true, false, true, false, List.of()));
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenReturn(response(output));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsValidationFailed_whenDenyingOnlyCurrentStatusSnapshotAvailable() {
    ExecutionAnalysisOutput output =
        new ExecutionAnalysisOutput(
            "PAYMENT_PENDING",
            List.of("STARTED"),
            "summary",
            new DataCompleteness(false, false, false, false, List.of()));
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenReturn(response(output));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.VALIDATION_FAILED);
  }

  @Test
  void execute_returnsProviderError_whenProviderThrowsAiModelException() {
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenThrow(new AiModelException("boom"));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.status()).isEqualTo(StageStatus.PROVIDER_ERROR);
  }

  @Test
  void execute_returnsTimeout_whenProviderThrowsSocketTimeoutException() {
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenThrow(new AiModelException("timed out", new SocketTimeoutException("read timed out")));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.TIMEOUT);
  }

  @Test
  void execute_returnsUnexpectedError_whenProviderThrowsUncheckedException() {
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenThrow(new IllegalStateException("something else broke"));

    var result = stage.execute(new ExecutionAnalysisInput(sagaSnapshot()), ANALYSIS_ID);

    assertThat(result.status()).isEqualTo(StageStatus.UNEXPECTED_ERROR);
  }

  @Test
  void execute_succeedsWithNonEmptyLimitations_forMinimalSagaInformation() {
    // A saga with only STARTED reached and no cancellation reason — proving the "surface the
    // limitation, don't invent" rule holds even for the thinnest possible input.
    SagaSnapshot minimalSaga =
        new SagaSnapshot(
            UUID.randomUUID(),
            "order-1",
            null,
            0,
            "STARTED",
            null,
            Instant.now(),
            Instant.now(),
            List.of("STARTED"));
    ExecutionAnalysisOutput output =
        honestOutput(List.of("Only current status known; no cancellation reason"));
    when(aiModelClient.generate(any(), eq(ExecutionAnalysisOutput.class)))
        .thenReturn(response(output));

    var result = stage.execute(new ExecutionAnalysisInput(minimalSaga), ANALYSIS_ID);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.output().dataCompleteness().limitations()).isNotEmpty();
  }

  private ExecutionAnalysisOutput honestOutput(List<String> limitations) {
    return new ExecutionAnalysisOutput(
        "PAYMENT_PENDING",
        List.of("STARTED", "INVENTORY_PENDING", "INVENTORY_CONFIRMED", "PAYMENT_PENDING"),
        "Saga is awaiting payment confirmation.",
        new DataCompleteness(true, false, false, false, limitations));
  }

  private AiModelResponse<ExecutionAnalysisOutput> response(ExecutionAnalysisOutput output) {
    return new AiModelResponse<>(output, "{}", 100, 50, 250L, "gpt-4o-mini", "openai");
  }

  private SagaSnapshot sagaSnapshot() {
    return new SagaSnapshot(
        UUID.randomUUID(),
        "order-1",
        "product-A",
        2,
        "PAYMENT_PENDING",
        null,
        Instant.now(),
        Instant.now(),
        List.of("STARTED", "INVENTORY_PENDING", "INVENTORY_CONFIRMED", "PAYMENT_PENDING"));
  }
}
