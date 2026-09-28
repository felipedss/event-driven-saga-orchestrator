package com.platform.saga.orchestrator.ai.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.platform.saga.orchestrator.ai.provider.AiModelException;
import com.platform.saga.orchestrator.ai.provider.AiModelResponse;
import com.platform.saga.orchestrator.ai.provider.AiRequest;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.execution.ExecutionAnalysisOutput;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

/** Mirrors {@code event-driven-order-service}'s {@code InventoryClientTest} pattern. */
class OpenAiModelClientTest {

  private static final String BASE_URL = "https://api.openai.test/v1";

  private MockRestServiceServer server;
  private OpenAiModelClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);
    RestClient restClient =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(restTemplate.getRequestFactory())
            .build();
    OpenAiClientProperties properties =
        new OpenAiClientProperties(
            BASE_URL,
            "test-key",
            "gpt-4o-mini",
            Duration.ofSeconds(5),
            Duration.ofSeconds(20),
            0.2);
    client = new OpenAiModelClient(restClient, properties, new ObjectMapper());
  }

  @Test
  void generate_returnsParsedOutput_onHappyPath() {
    String content =
        """
        {"currentState":"PAYMENT_PENDING",
         "reachedPhases":["STARTED","INVENTORY_PENDING"],
         "narrativeSummary":"Saga is awaiting payment confirmation.",
         "dataCompleteness":{"onlyCurrentStatusSnapshotAvailable":true,
           "stepByStepEventHistoryAvailable":false,"dlqCheckAvailable":false,
           "retryCountKnown":false,"limitations":["Only current status known"]}}
        """;
    server
        .expect(requestTo(BASE_URL + "/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
        .andExpect(jsonPath("$.response_format.type").value("json_schema"))
        .andRespond(withSuccess(chatCompletionResponse(content), MediaType.APPLICATION_JSON));

    AiModelResponse<ExecutionAnalysisOutput> response =
        client.generate(
            new AiRequest("system", "user", ExecutionAnalysisOutput.JSON_SCHEMA),
            ExecutionAnalysisOutput.class);

    assertThat(response.output().currentState()).isEqualTo("PAYMENT_PENDING");
    assertThat(response.output().dataCompleteness().limitations()).isNotEmpty();
    assertThat(response.promptTokens()).isEqualTo(100);
    assertThat(response.completionTokens()).isEqualTo(50);
    assertThat(response.provider()).isEqualTo("openai");
    server.verify();
  }

  @Test
  void generate_throwsAiModelException_whenContentIsNotValidJson() {
    server
        .expect(requestTo(BASE_URL + "/chat/completions"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(chatCompletionResponse("not valid json"), MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                client.generate(
                    new AiRequest("system", "user", ExecutionAnalysisOutput.JSON_SCHEMA),
                    ExecutionAnalysisOutput.class))
        .isInstanceOf(AiModelException.class);
    server.verify();
  }

  @Test
  void generate_throwsAiModelException_whenRequiredFieldIsMissing() {
    String content =
        """
        {"currentState":"PAYMENT_PENDING","reachedPhases":["STARTED"]}
        """;
    server
        .expect(requestTo(BASE_URL + "/chat/completions"))
        .andRespond(withSuccess(chatCompletionResponse(content), MediaType.APPLICATION_JSON));

    // dataCompleteness/narrativeSummary missing entirely is still valid JSON that Jackson can
    // deserialize into a record with null fields — the fabrication-guard rejection of that
    // belongs to the stage's domain validation (ExecutionAnalysisStageTest), not this client.
    AiModelResponse<ExecutionAnalysisOutput> response =
        client.generate(
            new AiRequest("system", "user", ExecutionAnalysisOutput.JSON_SCHEMA),
            ExecutionAnalysisOutput.class);

    assertThat(response.output().dataCompleteness()).isNull();
    server.verify();
  }

  @Test
  void generate_throwsAiModelException_onServerError() {
    server.expect(requestTo(BASE_URL + "/chat/completions")).andRespond(withServerError());

    assertThatThrownBy(
            () ->
                client.generate(
                    new AiRequest("system", "user", ExecutionAnalysisOutput.JSON_SCHEMA),
                    ExecutionAnalysisOutput.class))
        .isInstanceOf(AiModelException.class);
    server.verify();
  }

  @Test
  void generate_throwsAiModelException_whenResponseHasNoChoices() {
    server
        .expect(requestTo(BASE_URL + "/chat/completions"))
        .andRespond(
            withSuccess(
                """
            {"model":"gpt-4o-mini","choices":[],"usage":{"prompt_tokens":1,"completion_tokens":1}}
            """,
                MediaType.APPLICATION_JSON));

    assertThatThrownBy(
            () ->
                client.generate(
                    new AiRequest("system", "user", ExecutionAnalysisOutput.JSON_SCHEMA),
                    ExecutionAnalysisOutput.class))
        .isInstanceOf(AiModelException.class);
    server.verify();
  }

  private String chatCompletionResponse(String content) {
    String escapedContent = content.replace("\"", "\\\"").replace("\n", "\\n");
    return """
        {"model":"gpt-4o-mini","choices":[{"message":{"role":"assistant","content":"%s"}}],
         "usage":{"prompt_tokens":100,"completion_tokens":50}}
        """
        .formatted(escapedContent);
  }
}
