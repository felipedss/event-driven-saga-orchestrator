package com.platform.saga.orchestrator.ai.controller;

import com.platform.saga.orchestrator.ai.controller.dto.SagaAnalysisResponse;
import com.platform.saga.orchestrator.ai.service.SagaAnalyzerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/sagas")
@RequiredArgsConstructor
public class AiSagaAnalyzerController {

  private final SagaAnalyzerService sagaAnalyzerService;

  @PostMapping("/{sagaId}/analyze")
  public ResponseEntity<SagaAnalysisResponse> analyze(@PathVariable UUID sagaId) {
    return ResponseEntity.ok(sagaAnalyzerService.analyze(sagaId));
  }
}
