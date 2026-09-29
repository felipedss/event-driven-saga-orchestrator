package com.platform.saga.orchestrator.ai.provider.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Only the fields this client actually reads. Unknown fields (e.g. {@code id}, {@code created},
 * {@code object}) are ignored — Spring Boot's auto-configured {@code ObjectMapper} disables {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} by default.
 */
public record OpenAiChatCompletionResponse(String model, List<Choice> choices, Usage usage) {

  public record Choice(Message message) {}

  public record Message(String role, String content) {}

  public record Usage(
      @JsonProperty("prompt_tokens") Integer promptTokens,
      @JsonProperty("completion_tokens") Integer completionTokens) {}
}
