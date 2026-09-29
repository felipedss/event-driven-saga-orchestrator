package com.platform.saga.orchestrator.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OrderSagaTest {

  @Test
  void onCreate_setsCreatedAtAndUpdatedAt() {
    OrderSaga saga = new OrderSaga();

    ReflectionTestUtils.invokeMethod(saga, "onCreate");

    assertThat(saga.getCreatedAt()).isNotNull();
    assertThat(saga.getUpdatedAt()).isNotNull();
    assertThat(saga.getCreatedAt()).isEqualTo(saga.getUpdatedAt());
  }

  @Test
  void onUpdate_updatesUpdatedAtButNotCreatedAt() {
    OrderSaga saga = new OrderSaga();
    Instant originalCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
    saga.setCreatedAt(originalCreatedAt);
    saga.setUpdatedAt(originalCreatedAt);

    ReflectionTestUtils.invokeMethod(saga, "onUpdate");

    assertThat(saga.getCreatedAt()).isEqualTo(originalCreatedAt);
    assertThat(saga.getUpdatedAt()).isNotEqualTo(originalCreatedAt);
  }
}
