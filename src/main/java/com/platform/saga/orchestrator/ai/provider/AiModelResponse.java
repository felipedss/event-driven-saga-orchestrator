package com.platform.saga.orchestrator.ai.provider;

/**
 * A provider-neutral generation result, carrying enough telemetry for stage-level observability.
 */
public record AiModelResponse<T>(
    T output,
    String rawContent,
    Integer promptTokens,
    Integer completionTokens,
    long latencyMs,
    String model,
    String provider) {}
