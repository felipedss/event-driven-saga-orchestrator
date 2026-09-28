package com.platform.saga.orchestrator.ai.provider.openai.config;

import com.platform.saga.orchestrator.ai.provider.openai.OpenAiClientProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Mirrors {@code event-driven-order-service}'s {@code ExternalClientConfig} pattern. */
@Configuration
@RequiredArgsConstructor
public class OpenAiClientConfig {

  private final OpenAiClientProperties properties;

  @Bean
  public RestClient openAiRestClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .defaultHeader("Authorization", "Bearer " + properties.apiKey())
        .requestFactory(factory)
        .build();
  }
}
