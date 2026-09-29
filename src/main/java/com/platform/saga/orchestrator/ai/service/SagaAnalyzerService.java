package com.platform.saga.orchestrator.ai.service;

import com.platform.saga.orchestrator.ai.controller.dto.SagaAnalysisResponse;
import com.platform.saga.orchestrator.ai.exception.SagaNotFoundException;
import com.platform.saga.orchestrator.ai.model.SagaSnapshot;
import com.platform.saga.orchestrator.ai.workflow.AiWorkflow;
import com.platform.saga.orchestrator.ai.workflow.WorkflowResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.SagaAnalysisResult;
import com.platform.saga.orchestrator.model.OrderSaga;
import com.platform.saga.orchestrator.repository.SagaRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * The Saga use case. Depends on {@link SagaRepository} and the {@link AiWorkflow} interface only —
 * it is structurally impossible for this class to mutate saga state or publish Kafka messages,
 * since {@code SagaService} and {@code KafkaProducerService} are never injected.
 *
 * <p>Uses an explicit hand-written constructor, not Lombok {@code @RequiredArgsConstructor}, so
 * {@code @Qualifier} is visibly attached to the constructor parameter Spring actually resolves,
 * rather than relying on Lombok's annotation-copying behavior to propagate a field-level
 * {@code @Qualifier} onto a generated constructor parameter.
 */
@Service
public class SagaAnalyzerService {

  private final SagaRepository sagaRepository;
  private final AiWorkflow<SagaSnapshot, SagaAnalysisResult> sagaAnalysisWorkflow;

  public SagaAnalyzerService(
      SagaRepository sagaRepository,
      @Qualifier("sagaAnalysisWorkflow")
          AiWorkflow<SagaSnapshot, SagaAnalysisResult> sagaAnalysisWorkflow) {
    this.sagaRepository = sagaRepository;
    this.sagaAnalysisWorkflow = sagaAnalysisWorkflow;
  }

  public SagaAnalysisResponse analyze(UUID sagaId) {
    OrderSaga saga =
        sagaRepository.findById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));
    SagaSnapshot snapshot = SagaSnapshot.from(saga);
    UUID analysisId = UUID.randomUUID();
    WorkflowResult<SagaAnalysisResult> result = sagaAnalysisWorkflow.run(snapshot, analysisId);
    return SagaAnalysisResponse.from(result);
  }
}
