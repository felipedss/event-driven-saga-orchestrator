package com.platform.saga.orchestrator.ai.workflow;

import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import java.util.List;
import java.util.UUID;

/**
 * The outcome of a full workflow run. {@code complete} is {@code false} whenever any stage did not
 * succeed — {@code output} then carries whatever partial result the earlier, successful stages
 * produced, never a fabricated later stage.
 */
public record WorkflowResult<O>(
    UUID analysisId, boolean complete, List<StageResult<?>> stageSummaries, O output) {}
