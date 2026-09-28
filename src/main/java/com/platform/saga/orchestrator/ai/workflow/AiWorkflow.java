package com.platform.saga.orchestrator.ai.workflow;

import java.util.UUID;

/**
 * Generic contract for an AI-driven analysis workflow. Currently implemented only by a prompt chain
 * ({@code SagaAnalysisPromptChain}); a future routing, parallelization, or tool-using
 * implementation would be a sibling implementation of this same interface, not a replacement of it.
 */
public interface AiWorkflow<I, O> {

  WorkflowResult<O> run(I input, UUID analysisId);
}
