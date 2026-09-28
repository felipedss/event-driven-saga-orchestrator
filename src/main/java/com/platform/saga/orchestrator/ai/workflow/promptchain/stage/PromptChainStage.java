package com.platform.saga.orchestrator.ai.workflow.promptchain.stage;

import java.util.UUID;

public interface PromptChainStage<I, O> {

  StageResult<O> execute(I input, UUID analysisId);
}
