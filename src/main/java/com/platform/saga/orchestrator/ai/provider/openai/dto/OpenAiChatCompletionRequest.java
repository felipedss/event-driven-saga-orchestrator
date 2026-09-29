package com.platform.saga.orchestrator.ai.provider.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record OpenAiChatCompletionRequest(
    String model,
    List<OpenAiChatMessage> messages,
    @JsonProperty("response_format") OpenAiResponseFormat responseFormat,
    Double temperature) {}
