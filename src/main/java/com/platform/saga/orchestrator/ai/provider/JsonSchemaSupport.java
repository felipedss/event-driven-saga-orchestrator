package com.platform.saga.orchestrator.ai.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written helpers for building the small JSON Schema {@code Map<String, Object>} literals each
 * stage attaches to its own output record. Not a schema-generation library — every stage still
 * writes out its own schema explicitly; this only removes the boilerplate that strict mode requires
 * on every object ({@code additionalProperties: false}, every property listed in {@code required}).
 */
public final class JsonSchemaSupport {

  private JsonSchemaSupport() {}

  public static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  public static Map<String, Object> bool() {
    return Map.of("type", "boolean");
  }

  public static Map<String, Object> number() {
    return Map.of("type", "number");
  }

  public static Map<String, Object> stringEnum(List<String> values) {
    return Map.of("type", "string", "enum", values);
  }

  public static Map<String, Object> arrayOf(Map<String, Object> items) {
    return Map.of("type", "array", "items", items);
  }

  public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }

  /** Builds a properties map from alternating key/schema pairs, in declaration order. */
  public static Map<String, Object> properties(Object... keyValuePairs) {
    Map<String, Object> props = new LinkedHashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      props.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return props;
  }
}
