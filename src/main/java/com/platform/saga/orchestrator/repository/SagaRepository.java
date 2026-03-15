package com.platform.saga.orchestrator.repository;

import com.platform.saga.orchestrator.model.OrderSaga;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SagaRepository extends JpaRepository<OrderSaga, UUID> {
  Optional<OrderSaga> findByOrderId(String orderId);
}
