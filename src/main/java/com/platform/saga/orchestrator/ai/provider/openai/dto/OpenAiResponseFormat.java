package com.platform.saga.orchestrator.ai.provider.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** OpenAI's {@code response_format} request shape for strict Structured Outputs. */
public record OpenAiResponseFormat(
    String type, @JsonProperty("json_schema") JsonSchemaSpec jsonSchema) {

  public static OpenAiResponseFormat strictJsonSchema(String name, Map<String, Object> schema) {
    return new OpenAiResponseFormat("json_schema", new JsonSchemaSpec(name, true, schema));
  }

  public record JsonSchemaSpec(String name, boolean strict, Map<String, Object> schema) {}
}
