package com.platform.saga.orchestrator.command;

public record OrderConfirmedCommand(String orderId, String productId) {}
