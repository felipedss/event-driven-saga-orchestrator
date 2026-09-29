package com.platform.saga.orchestrator.ai.controller.dto;

import com.platform.saga.orchestrator.ai.workflow.WorkflowResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.SagaAnalysisResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationOutput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;
import java.util.List;
import java.util.UUID;

/**
 * The endpoint's response envelope, chosen to stay stable as the internal workflow evolves from a
 * 2-stage chain to a 4-stage chain (Phase 3) to a non-chain agentic pattern (Future) — none of that
 * should ever require a breaking API change.
 */
public record SagaAnalysisResponse(
    UUID analysisId,
    boolean complete,
    ExecutionAnalysisOutput executionAnalysis,
    FailureClassificationOutput failureClassification,
    List<StageSummary> stageSummaries) {

  public static SagaAnalysisResponse from(WorkflowResult<SagaAnalysisResult> result) {
    SagaAnalysisResult output = result.output();
    return new SagaAnalysisResponse(
        result.analysisId(),
        result.complete(),
        output != null ? output.executionAnalysis() : null,
        output != null ? output.failureClassification() : null,
        result.stageSummaries().stream().map(StageSummary::from).toList());
  }
}
