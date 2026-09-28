package com.platform.saga.orchestrator.ai.controller.dto;

import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageTelemetry;

public record StageSummary(
    String stageName, String status, String errorMessage, String model, Long latencyMs) {

  public static StageSummary from(StageResult<?> stageResult) {
    StageTelemetry telemetry = stageResult.telemetry();
    return new StageSummary(
        stageResult.stageName().name(),
        stageResult.status().name(),
        stageResult.errorMessage(),
        telemetry != null ? telemetry.model() : null,
        telemetry != null ? telemetry.latencyMs() : null);
  }
}
