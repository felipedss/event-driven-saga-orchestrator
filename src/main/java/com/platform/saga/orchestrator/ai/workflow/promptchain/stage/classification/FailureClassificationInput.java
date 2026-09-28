package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;

public record FailureClassificationInput(
    ExecutionAnalysisOutput executionAnalysis, String cancellationReason) {}
