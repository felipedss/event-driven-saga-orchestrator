package com.platform.saga.orchestrator.ai.controller;

import com.platform.saga.orchestrator.ai.controller.dto.AiAnalysisErrorResponse;
import com.platform.saga.orchestrator.ai.exception.SagaNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackageClasses = AiSagaAnalyzerController.class)
public class AiAnalyzerExceptionHandler {

  @ExceptionHandler(SagaNotFoundException.class)
  public ResponseEntity<AiAnalysisErrorResponse> handleNotFound(SagaNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new AiAnalysisErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<AiAnalysisErrorResponse> handleUnexpected(Exception e) {
    log.error("Unexpected error in AI saga analyzer", e);
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(new AiAnalysisErrorResponse("AI saga analysis failed unexpectedly"));
  }
}
