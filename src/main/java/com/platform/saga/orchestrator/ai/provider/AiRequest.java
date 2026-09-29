package com.platform.saga.orchestrator.ai.provider;

import java.util.Map;

/**
 * A provider-neutral generation request. {@code outputJsonSchema} is a plain JSON Schema {@link
 * Map} — how (or whether) a given {@link AiModelClient} implementation enforces it is that
 * implementation's concern, not the caller's.
 */
public record AiRequest(
    String systemPrompt, String userPrompt, Map<String, Object> outputJsonSchema) {}
