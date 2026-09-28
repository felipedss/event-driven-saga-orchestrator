package com.platform.saga.orchestrator.ai.provider;

/**
 * Unchecked, provider-neutral failure type. Every {@link AiModelClient} implementation wraps any
 * HTTP failure, timeout, missing/empty response, or JSON that doesn't deserialize into the
 * requested output type as this — never a raw, provider-specific exception.
 */
public class AiModelException extends RuntimeException {

  public AiModelException(String message) {
    super(message);
  }

  public AiModelException(String message, Throwable cause) {
    super(message, cause);
  }
}
