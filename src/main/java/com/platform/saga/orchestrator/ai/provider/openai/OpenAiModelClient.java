package com.platform.saga.orchestrator.ai.provider.openai;

import com.platform.saga.orchestrator.ai.provider.AiModelClient;
import com.platform.saga.orchestrator.ai.provider.AiModelException;
import com.platform.saga.orchestrator.ai.provider.AiModelResponse;
import com.platform.saga.orchestrator.ai.provider.AiRequest;
import com.platform.saga.orchestrator.ai.provider.openai.dto.OpenAiChatCompletionRequest;
import com.platform.saga.orchestrator.ai.provider.openai.dto.OpenAiChatCompletionResponse;
import com.platform.saga.orchestrator.ai.provider.openai.dto.OpenAiChatMessage;
import com.platform.saga.orchestrator.ai.provider.openai.dto.OpenAiResponseFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiModelClient implements AiModelClient {

  private static final String STRUCTURED_OUTPUT_SCHEMA_NAME = "stage_output";

  private final RestClient openAiRestClient;
  private final OpenAiClientProperties properties;
  private final ObjectMapper objectMapper;

  @Override
  public <T> AiModelResponse<T> generate(AiRequest request, Class<T> outputType) {
    long start = System.currentTimeMillis();

    OpenAiChatCompletionRequest chatRequest =
        new OpenAiChatCompletionRequest(
            properties.model(),
            List.of(
                new OpenAiChatMessage("system", request.systemPrompt()),
                new OpenAiChatMessage("user", request.userPrompt())),
            OpenAiResponseFormat.strictJsonSchema(
                STRUCTURED_OUTPUT_SCHEMA_NAME, request.outputJsonSchema()),
            properties.temperature());

    OpenAiChatCompletionResponse response;
    try {
      response =
          openAiRestClient
              .post()
              .uri("/chat/completions")
              .body(chatRequest)
              .retrieve()
              .body(OpenAiChatCompletionResponse.class);
    } catch (Exception e) {
      throw new AiModelException("OpenAI request failed: " + e.getMessage(), e);
    }

    String rawContent = extractContent(response);
    T output;
    try {
      output = objectMapper.readValue(rawContent, outputType);
    } catch (Exception e) {
      throw new AiModelException(
          "Failed to parse OpenAI response into " + outputType.getSimpleName(), e);
    }

    long latencyMs = System.currentTimeMillis() - start;
    OpenAiChatCompletionResponse.Usage usage = response.usage();
    return new AiModelResponse<>(
        output,
        rawContent,
        usage != null ? usage.promptTokens() : null,
        usage != null ? usage.completionTokens() : null,
        latencyMs,
        response.model(),
        "openai");
  }

  private String extractContent(OpenAiChatCompletionResponse response) {
    if (response == null
        || response.choices() == null
        || response.choices().isEmpty()
        || response.choices().get(0).message() == null
        || response.choices().get(0).message().content() == null) {
      throw new AiModelException("OpenAI returned an empty or malformed response");
    }
    return response.choices().get(0).message().content();
  }
}
