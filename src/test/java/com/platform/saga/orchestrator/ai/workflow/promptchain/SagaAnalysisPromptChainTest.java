package com.platform.saga.orchestrator.ai.workflow.promptchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.platform.saga.orchestrator.ai.model.SagaSnapshot;
import com.platform.saga.orchestrator.ai.workflow.WorkflowResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageName;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.ExecutionAssessment;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureCategory;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationInput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationOutput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationStage;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.DataCompleteness;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisStage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaAnalysisPromptChainTest {

  @Mock private ExecutionAnalysisStage executionAnalysisStage;
  @Mock private FailureClassificationStage failureClassificationStage;
  @InjectMocks private SagaAnalysisPromptChain chain;

  private static final UUID ANALYSIS_ID = UUID.randomUUID();

  @Test
  void run_passesStage1OutputExactlyIntoStage2Input() {
    ExecutionAnalysisOutput executionOutput = executionOutput();
    when(executionAnalysisStage.execute(any(), eq(ANALYSIS_ID)))
        .thenReturn(StageResult.success(StageName.EXECUTION_ANALYSIS, executionOutput, null));
    FailureClassificationOutput classificationOutput =
        new FailureClassificationOutput(
            FailureCategory.UNKNOWN, ExecutionAssessment.IN_PROGRESS, 0.7, "r");
    when(failureClassificationStage.execute(any(), eq(ANALYSIS_ID)))
        .thenReturn(
            StageResult.success(StageName.FAILURE_CLASSIFICATION, classificationOutput, null));

    WorkflowResult<SagaAnalysisResult> result = chain.run(sagaSnapshot("reason"), ANALYSIS_ID);

    ArgumentCaptor<FailureClassificationInput> captor =
        ArgumentCaptor.forClass(FailureClassificationInput.class);
    verify(failureClassificationStage).execute(captor.capture(), eq(ANALYSIS_ID));
    assertThat(captor.getValue().executionAnalysis()).isEqualTo(executionOutput);
    assertThat(captor.getValue().cancellationReason()).isEqualTo("reason");
    assertThat(result.complete()).isTrue();
    assertThat(result.output().executionAnalysis()).isEqualTo(executionOutput);
    assertThat(result.output().failureClassification()).isEqualTo(classificationOutput);
  }

  @Test
  void run_neverInvokesStage2_whenStage1Fails() {
    when(executionAnalysisStage.execute(any(), eq(ANALYSIS_ID)))
        .thenReturn(
            StageResult.failure(
                StageName.EXECUTION_ANALYSIS, StageStatus.PROVIDER_ERROR, "boom", null));

    WorkflowResult<SagaAnalysisResult> result = chain.run(sagaSnapshot(null), ANALYSIS_ID);

    verify(failureClassificationStage, never()).execute(any(), any());
    assertThat(result.complete()).isFalse();
    assertThat(result.output().executionAnalysis()).isNull();
    assertThat(result.output().failureClassification()).isNull();
    assertThat(result.stageSummaries()).hasSize(1);
  }

  @Test
  void run_isIncomplete_whenStage2Fails() {
    ExecutionAnalysisOutput executionOutput = executionOutput();
    when(executionAnalysisStage.execute(any(), eq(ANALYSIS_ID)))
        .thenReturn(StageResult.success(StageName.EXECUTION_ANALYSIS, executionOutput, null));
    when(failureClassificationStage.execute(any(), eq(ANALYSIS_ID)))
        .thenReturn(
            StageResult.failure(
                StageName.FAILURE_CLASSIFICATION,
                StageStatus.VALIDATION_FAILED,
                "bad output",
                null));

    WorkflowResult<SagaAnalysisResult> result = chain.run(sagaSnapshot(null), ANALYSIS_ID);

    assertThat(result.complete()).isFalse();
    assertThat(result.output().executionAnalysis()).isEqualTo(executionOutput);
    assertThat(result.output().failureClassification()).isNull();
    assertThat(result.stageSummaries()).hasSize(2);
  }

  private ExecutionAnalysisOutput executionOutput() {
    return new ExecutionAnalysisOutput(
        "PAYMENT_PENDING",
        List.of("STARTED", "INVENTORY_PENDING"),
        "summary",
        new DataCompleteness(true, false, false, false, List.of("limitation")));
  }

  private SagaSnapshot sagaSnapshot(String cancellationReason) {
    return new SagaSnapshot(
        UUID.randomUUID(),
        "order-1",
        "product-A",
        1,
        "PAYMENT_PENDING",
        cancellationReason,
        Instant.now(),
        Instant.now(),
        List.of("STARTED", "INVENTORY_PENDING"));
  }
}
