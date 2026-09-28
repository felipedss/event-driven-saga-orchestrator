package com.platform.saga.orchestrator.ai.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.platform.saga.orchestrator.ai.controller.dto.SagaAnalysisResponse;
import com.platform.saga.orchestrator.ai.controller.dto.StageSummary;
import com.platform.saga.orchestrator.ai.exception.SagaNotFoundException;
import com.platform.saga.orchestrator.ai.service.SagaAnalyzerService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AiSagaAnalyzerControllerTest {

  @Mock private SagaAnalyzerService sagaAnalyzerService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AiSagaAnalyzerController(sagaAnalyzerService))
            .setControllerAdvice(new AiAnalyzerExceptionHandler())
            .build();
  }

  @Test
  void analyze_returns200_whenBothStagesSucceed() throws Exception {
    UUID sagaId = UUID.randomUUID();
    SagaAnalysisResponse response =
        new SagaAnalysisResponse(UUID.randomUUID(), true, null, null, List.of());
    when(sagaAnalyzerService.analyze(sagaId)).thenReturn(response);

    mockMvc
        .perform(post("/ai/sagas/{sagaId}/analyze", sagaId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(true));
  }

  @Test
  void analyze_returns200WithIncomplete_whenAStageFails() throws Exception {
    UUID sagaId = UUID.randomUUID();
    SagaAnalysisResponse response =
        new SagaAnalysisResponse(
            UUID.randomUUID(),
            false,
            null,
            null,
            List.of(
                new StageSummary("FAILURE_CLASSIFICATION", "PROVIDER_ERROR", "boom", null, null)));
    when(sagaAnalyzerService.analyze(sagaId)).thenReturn(response);

    mockMvc
        .perform(post("/ai/sagas/{sagaId}/analyze", sagaId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.complete").value(false));
  }

  @Test
  void analyze_returns404_whenSagaNotFound() throws Exception {
    UUID sagaId = UUID.randomUUID();
    when(sagaAnalyzerService.analyze(sagaId)).thenThrow(new SagaNotFoundException(sagaId));

    mockMvc.perform(post("/ai/sagas/{sagaId}/analyze", sagaId)).andExpect(status().isNotFound());
  }

  @Test
  void analyze_returns502_onUnexpectedError() throws Exception {
    UUID sagaId = UUID.randomUUID();
    when(sagaAnalyzerService.analyze(sagaId)).thenThrow(new RuntimeException("unexpected"));

    mockMvc.perform(post("/ai/sagas/{sagaId}/analyze", sagaId)).andExpect(status().isBadGateway());
  }
}
