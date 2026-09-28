package com.platform.saga.orchestrator.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.saga.orchestrator.ai.controller.dto.SagaAnalysisResponse;
import com.platform.saga.orchestrator.ai.exception.SagaNotFoundException;
import com.platform.saga.orchestrator.ai.model.SagaSnapshot;
import com.platform.saga.orchestrator.ai.workflow.AiWorkflow;
import com.platform.saga.orchestrator.ai.workflow.WorkflowResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.SagaAnalysisResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageName;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageResult;
import com.platform.saga.orchestrator.ai.workflow.promptchain.stage.StageStatus;
import com.platform.saga.orchestrator.model.OrderSaga;
import com.platform.saga.orchestrator.model.SagaStatus;
import com.platform.saga.orchestrator.repository.SagaRepository;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SagaAnalyzerServiceTest {

  @Mock private SagaRepository sagaRepository;
  @Mock private AiWorkflow<SagaSnapshot, SagaAnalysisResult> sagaAnalysisWorkflow;

  private SagaAnalyzerService sagaAnalyzerService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    sagaAnalyzerService = new SagaAnalyzerService(sagaRepository, sagaAnalysisWorkflow);
  }

  @Test
  void isStructurallyIsolatedFromSagaTransactionExecution() {
    // It must be impossible for this class to mutate saga state or publish Kafka messages —
    // proven here by inspecting the constructor it actually has, not by trusting a convention.
    Constructor<?>[] constructors = SagaAnalyzerService.class.getDeclaredConstructors();
    assertThat(constructors).hasSize(1);
    List<Class<?>> parameterTypes = List.of(constructors[0].getParameterTypes());
    assertThat(parameterTypes).containsExactly(SagaRepository.class, AiWorkflow.class);
    assertThat(parameterTypes)
        .noneMatch(type -> type.getSimpleName().equals("SagaService"))
        .noneMatch(type -> type.getSimpleName().equals("KafkaProducerService"));
  }

  @Test
  void analyze_throwsSagaNotFoundException_whenSagaDoesNotExist() {
    UUID sagaId = UUID.randomUUID();
    when(sagaRepository.findById(sagaId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> sagaAnalyzerService.analyze(sagaId))
        .isInstanceOf(SagaNotFoundException.class);
  }

  @Test
  void analyze_returnsCompleteResponse_whenWorkflowSucceeds() {
    UUID sagaId = UUID.randomUUID();
    OrderSaga saga = new OrderSaga();
    saga.setSagaId(sagaId);
    saga.setOrderId("order-1");
    saga.setStatus(SagaStatus.PAYMENT_PENDING);
    when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(saga));

    StageResult<Object> stageResult =
        StageResult.success(StageName.EXECUTION_ANALYSIS, new Object(), null);
    WorkflowResult<SagaAnalysisResult> workflowResult =
        new WorkflowResult<>(
            UUID.randomUUID(), true, List.of(stageResult), new SagaAnalysisResult(null, null));
    when(sagaAnalysisWorkflow.run(any(), any())).thenReturn(workflowResult);

    SagaAnalysisResponse response = sagaAnalyzerService.analyze(sagaId);

    assertThat(response.complete()).isTrue();
  }

  @Test
  void analyze_returnsDegradedResponse_ratherThanThrowing_whenProviderIsUnavailable() {
    UUID sagaId = UUID.randomUUID();
    OrderSaga saga = new OrderSaga();
    saga.setSagaId(sagaId);
    saga.setOrderId("order-1");
    saga.setStatus(SagaStatus.PAYMENT_PENDING);
    when(sagaRepository.findById(sagaId)).thenReturn(Optional.of(saga));

    StageResult<Object> failedStage =
        StageResult.failure(
            StageName.EXECUTION_ANALYSIS, StageStatus.PROVIDER_ERROR, "no API key", null);
    WorkflowResult<SagaAnalysisResult> workflowResult =
        new WorkflowResult<>(
            UUID.randomUUID(), false, List.of(failedStage), new SagaAnalysisResult(null, null));
    when(sagaAnalysisWorkflow.run(any(), any())).thenReturn(workflowResult);

    SagaAnalysisResponse response = sagaAnalyzerService.analyze(sagaId);

    assertThat(response.complete()).isFalse();
    assertThat(response.executionAnalysis()).isNull();
  }
}
