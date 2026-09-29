package com.platform.saga.orchestrator.ai.provider;

/**
 * Provider-neutral contract for calling an LLM. {@code ai/workflow/} is coded against this
 * interface only — never against a specific provider's SDK or wire format. Adding another provider
 * (Anthropic, Gemini, a local model) means one new implementing class, zero changes to {@code
 * ai/workflow/}.
 */
public interface AiModelClient {

  <T> AiModelResponse<T> generate(AiRequest request, Class<T> outputType);
}
