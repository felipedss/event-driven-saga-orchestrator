package com.platform.saga.orchestrator.ai.workflow.promptchain;

import com.platform.saga.orchestrator.ai.model.SagaSnapshot;
import com.platform.saga.orchestrator.ai.workflow.AiWorkflow;
import com.platform.saga.orchestrator.ai.workflow.WorkflowResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageName;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationInput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationOutput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationStage;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisInput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisStage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * One {@link AiWorkflow} implementation, specifically a prompt chain, for the saga-analysis use
 * case — not the architecture's permanent center. A future routing- or tool-use-based workflow
 * would be a sibling implementation of the same interface.
 *
 * <p>Runs Execution Analysis → Failure Classification in order; short-circuits into a partial
 * {@link WorkflowResult} if Execution Analysis fails, since Failure Classification depends on its
 * output.
 */
@Slf4j
@Component("sagaAnalysisWorkflow")
@RequiredArgsConstructor
public class SagaAnalysisPromptChain implements AiWorkflow<SagaSnapshot, SagaAnalysisResult> {

  private final ExecutionAnalysisStage executionAnalysisStage;
  private final FailureClassificationStage failureClassificationStage;

  @Override
  public WorkflowResult<SagaAnalysisResult> run(SagaSnapshot saga, UUID analysisId) {
    StageResult<ExecutionAnalysisOutput> executionResult = runExecutionAnalysis(saga, analysisId);
    if (!executionResult.isSuccess()) {
      return new WorkflowResult<>(
          analysisId, false, List.of(executionResult), new SagaAnalysisResult(null, null));
    }

    StageResult<FailureClassificationOutput> classificationResult =
        runFailureClassification(saga, executionResult.output(), analysisId);

    boolean complete = classificationResult.isSuccess();
    SagaAnalysisResult result =
        new SagaAnalysisResult(
            executionResult.output(), complete ? classificationResult.output() : null);
    List<StageResult<?>> stageSummaries = List.of(executionResult, classificationResult);
    return new WorkflowResult<>(analysisId, complete, stageSummaries, result);
  }

  private StageResult<ExecutionAnalysisOutput> runExecutionAnalysis(
      SagaSnapshot saga, UUID analysisId) {
    try {
      return executionAnalysisStage.execute(new ExecutionAnalysisInput(saga), analysisId);
    } catch (Exception e) {
      log.error("[{}] Execution Analysis stage threw unexpectedly", analysisId, e);
      return StageResult.failure(
          StageName.EXECUTION_ANALYSIS, StageStatus.UNEXPECTED_ERROR, e.getMessage(), null);
    }
  }

  private StageResult<FailureClassificationOutput> runFailureClassification(
      SagaSnapshot saga, ExecutionAnalysisOutput executionAnalysis, UUID analysisId) {
    try {
      return failureClassificationStage.execute(
          new FailureClassificationInput(
              executionAnalysis, saga.status(), saga.cancellationReason()),
          analysisId);
    } catch (Exception e) {
      log.error("[{}] Failure Classification stage threw unexpectedly", analysisId, e);
      return StageResult.failure(
          StageName.FAILURE_CLASSIFICATION, StageStatus.UNEXPECTED_ERROR, e.getMessage(), null);
    }
  }
}
