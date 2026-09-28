package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution;

import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.arrayOf;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.bool;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.object;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.properties;
import static com.platform.saga.orchestrator.ai.provider.JsonSchemaSupport.string;

import java.util.List;
import java.util.Map;

/**
 * The fabrication guard. {@code onlyCurrentStatusSnapshotAvailable} / {@code
 * stepByStepEventHistoryAvailable} / {@code dlqCheckAvailable} / {@code retryCountKnown} are not
 * opinions the model forms — they are facts about this milestone's data sources, and {@link
 * ExecutionAnalysisStage}'s validator rejects a response that claims otherwise.
 */
public record DataCompleteness(
    boolean onlyCurrentStatusSnapshotAvailable,
    boolean stepByStepEventHistoryAvailable,
    boolean dlqCheckAvailable,
    boolean retryCountKnown,
    List<String> limitations) {

  public static final Map<String, Object> JSON_SCHEMA =
      object(
          properties(
              "onlyCurrentStatusSnapshotAvailable", bool(),
              "stepByStepEventHistoryAvailable", bool(),
              "dlqCheckAvailable", bool(),
              "retryCountKnown", bool(),
              "limitations", arrayOf(string())),
          List.of(
              "onlyCurrentStatusSnapshotAvailable",
              "stepByStepEventHistoryAvailable",
              "dlqCheckAvailable",
              "retryCountKnown",
              "limitations"));
}
