package com.platform.saga.orchestrator.ai.workflow.promptchain.stage;

public record StageTelemetry(
    String model,
    String provider,
    long latencyMs,
    Integer promptTokens,
    Integer completionTokens) {}
