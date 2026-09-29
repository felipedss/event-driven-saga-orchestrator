package com.platform.saga.orchestrator.ai.provider.openai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiClientProperties(
    String baseUrl,
    String apiKey,
    String model,
    Duration connectTimeout,
    Duration readTimeout,
    double temperature) {}
