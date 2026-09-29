package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution;

import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.arrayOf;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.object;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.properties;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.string;

import java.util.List;
import java.util.Map;

/**
 * Renamed from "Timeline Reconstruction" (the name used in ADR-0018): "reconstruction" implies
 * recovering a sequence of past events, which this stage does not do — it interprets the current
 * snapshot plus the deterministically-known {@code reachedPhases}.
 */
public record ExecutionAnalysisOutput(
    String currentState,
    List<String> reachedPhases,
    String narrativeSummary,
    DataCompleteness dataCompleteness) {

  public static final Map<String, Object> JSON_SCHEMA =
      object(
          properties(
              "currentState", string(),
              "reachedPhases", arrayOf(string()),
              "narrativeSummary", string(),
              "dataCompleteness", DataCompleteness.JSON_SCHEMA),
          List.of("currentState", "reachedPhases", "narrativeSummary", "dataCompleteness"));
}
