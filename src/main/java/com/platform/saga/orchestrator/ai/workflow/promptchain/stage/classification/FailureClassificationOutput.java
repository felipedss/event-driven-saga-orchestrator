package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.number;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.object;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.properties;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.string;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.stringEnum;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public record FailureClassificationOutput(
    FailureCategory category,
    ExecutionAssessment executionAssessment,
    double confidence,
    String reasoning) {

  public static final Map<String, Object> JSON_SCHEMA =
      object(
          properties(
              "category", stringEnum(enumNames(FailureCategory.values())),
              "executionAssessment", stringEnum(enumNames(ExecutionAssessment.values())),
              "confidence", number(),
              "reasoning", string()),
          List.of("category", "executionAssessment", "confidence", "reasoning"));

  private static List<String> enumNames(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }
}
