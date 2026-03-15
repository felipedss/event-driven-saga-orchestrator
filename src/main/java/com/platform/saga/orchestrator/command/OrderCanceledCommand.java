package com.platform.saga.orchestrator.command;

public record OrderCanceledCommand(String orderId, String reason) {}
