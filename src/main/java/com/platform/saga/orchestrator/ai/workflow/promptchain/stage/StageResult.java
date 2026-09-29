package com.platform.saga.orchestrator.ai.workflow.promptchain.stage;

/**
 * The outcome of a single {@link PromptChainStage} invocation. A stage always returns one of these
 * — it never throws for a recoverable failure (provider error, timeout, malformed/invalid model
 * output); only a genuinely unexpected condition (a JVM {@link Error}) is allowed to propagate past
 * a stage.
 */
public record StageResult<O>(
    StageName stageName,
    StageStatus status,
    O output,
    String errorMessage,
    StageTelemetry telemetry) {

  public static <O> StageResult<O> success(
      StageName stageName, O output, StageTelemetry telemetry) {
    return new StageResult<>(stageName, StageStatus.SUCCESS, output, null, telemetry);
  }

  public static <O> StageResult<O> failure(
      StageName stageName, StageStatus status, String errorMessage, StageTelemetry telemetry) {
    return new StageResult<>(stageName, status, null, errorMessage, telemetry);
  }

  public boolean isSuccess() {
    return status == StageStatus.SUCCESS;
  }
}
