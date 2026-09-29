package com.platform.saga.orchestrator.ai.workflow.promptchain.stage.classification;

/**
 * Only the values the current system can support with evidence it has. There is no
 * timeout/manual-review signal (ADR-0015 not implemented), no DLQ read path, no per-saga retry
 * count, and no persisted step-by-step history — so this is limited to conclusions derivable from
 * the saga's current status and cancellation reason alone.
 *
 * <p>{@code COMPENSATING} is its own value, not folded into {@code TERMINAL_FAILURE}: a saga that
 * is compensating is still executing — inventory release, refunds, or similar undo actions are
 * actively in flight. Calling that "terminal" would say the saga is done when it demonstrably
 * isn't.
 */
public enum ExecutionAssessment {
  IN_PROGRESS,
  COMPENSATING,
  TERMINAL_SUCCESS,
  TERMINAL_FAILURE,
  UNKNOWN
}
