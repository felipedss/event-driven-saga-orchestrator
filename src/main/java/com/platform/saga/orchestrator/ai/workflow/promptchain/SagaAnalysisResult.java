package com.platform.saga.orchestrator.ai.workflow.promptchain;

import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification.FailureClassificationOutput;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;

/**
 * Aggregates the chain's stage outputs. Only these two fields exist in this milestone — {@code
 * recovery}/{@code explanation} fields are added in Phase 3, not stubbed out now. Either field is
 * {@code null} if its stage did not run or did not succeed.
 */
public record SagaAnalysisResult(
    ExecutionAnalysisOutput executionAnalysis, FailureClassificationOutput failureClassification) {}
