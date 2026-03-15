# Saga Orchestrator

A Spring Boot microservice that coordinates distributed transactions across the order platform using the **Saga Orchestration** pattern. When an order is created, this service drives the workflow across Payment, Inventory, and Notification services — confirming or compensating as needed.

## Overview

The Saga Orchestrator sits at the center of the distributed transaction. It consumes events from Kafka, persists saga state to PostgreSQL (so workflows survive restarts), and publishes reply events back to the Order Service.

```
order-service                  saga-orchestrator              downstream services
     │                               │
     │  OrderCreatedEvent            │
     │ ──── order.created ──────────►│
     │                               │  (Phase 2: ReserveInventoryCommand)
     │                               │ ─────── order.inventory.reserve ──────────►
     │                               │
     │                               │◄─── order.inventory.reserved ─────────────
     │                               │
     │                               │  (Phase 2: ChargePaymentCommand)
     │                               │ ─────── order.payment.charge ─────────────►
     │                               │
     │                               │◄─── order.payment.confirmed ───────────────
     │                               │
     │◄── order.confirmed ───────────│   ← saga COMPLETED
     │
     │    (on any failure)
     │◄── order.cancelled ───────────│   ← saga CANCELLED + compensations triggered
```

### Saga State Machine

Each saga is persisted as an `OrderSaga` entity and progresses through these states:

```
STARTED
  │
  ▼
INVENTORY_PENDING ──(failed)──► INVENTORY_FAILED ──► CANCELLED
  │
  ▼
INVENTORY_CONFIRMED
  │
  ▼
PAYMENT_PENDING ──(failed)──► PAYMENT_FAILED ──► CANCELLED
  │
  ▼
PAYMENT_CONFIRMED
  │
  ▼
COMPLETED
```

> **Phase 1 (current):** The orchestrator immediately transitions `STARTED → COMPLETED` and publishes `order.confirmed`. The full step-based state machine is implemented in Phase 2.

## Tech Stack

- **Java 17**, Spring Boot 4.0.3
- **PostgreSQL** — saga state persistence (Hibernate, `ddl-auto: update`)
- **Apache Kafka** — event consumption and publishing
- **Lombok** — boilerplate reduction
- **Spotless** — code formatting (Google Java Format)

## Prerequisites

- Docker (for Kafka and PostgreSQL)
- Java 17+
- Maven

## Infrastructure Setup

Start Kafka and PostgreSQL via the shared Docker Compose configuration:

```bash
cd ~/Workspace/event-driven-simulator/infrastructure/docker-compose
docker compose up -d
```

This starts:
- **PostgreSQL** on `localhost:5432` — database `orchestrator_db`, credentials `postgres/postgres`
- **Kafka** on `localhost:9094` (external) — KRaft mode, no Zookeeper
- **Kafka UI** at `http://localhost:8090`

## Running the Service

```bash
mvn spring-boot:run
```

The service starts on **port 8085**.

## Kafka Topics

| Topic | Direction | Event | Description |
|---|---|---|---|
| `order.created` | Inbound | `OrderCreatedEvent` | Triggers saga start |
| `order.inventory.reserved` | Inbound | `InventoryReservedEvent` | Inventory step succeeded |
| `order.confirmed` | Outbound | `OrderConfirmedEvent` | Saga completed — order confirmed |
| `order.cancelled` | Outbound | `OrderCancelledEvent` | Saga failed — order rolled back |

## Saga States

| Status | Description |
|---|---|
| `STARTED` | Saga initiated |
| `INVENTORY_PENDING` | Awaiting inventory reservation |
| `INVENTORY_CONFIRMED` | Inventory reserved successfully |
| `INVENTORY_FAILED` | Inventory reservation failed |
| `PAYMENT_PENDING` | Awaiting payment confirmation |
| `PAYMENT_CONFIRMED` | Payment successful |
| `PAYMENT_FAILED` | Payment failed |
| `COMPLETED` | All steps succeeded |
| `CANCELLED` | Saga rolled back due to failure |

## Build Commands

```bash
mvn clean install       # Build and run all tests
mvn test                # Run tests only
mvn spotless:apply      # Format code (run before committing)
mvn spotless:check      # Verify formatting
```

## Package Structure

```
com.platform.saga.orchestrator
├── core/
│   ├── Saga                    # Saga contract (interface)
│   ├── SagaStep                # Step contract: execute() + compensate()
│   └── SagaContext             # Context carrier passed between steps
├── config/
│   ├── KafkaProducerConfig     # JacksonJsonSerializer, type headers disabled
│   └── KafkaConsumerConfig     # JacksonJsonMessageConverter, type inference
├── event/
│   ├── OrderCreatedEvent       # Inbound — from order-service
│   ├── OrderConfirmedEvent     # Outbound — to order-service
│   ├── OrderCancelledEvent     # Outbound — to order-service
│   ├── InventoryReservedEvent  # Inbound — from inventory-service
│   └── InventoryReservationFailedEvent
├── model/
│   ├── OrderSaga               # JPA entity — persisted saga state
│   └── SagaStatus              # Enum of all saga lifecycle states
├── repository/
│   └── SagaRepository          # findByOrderId() + inherited CRUD
├── service/
│   ├── SagaService             # Core orchestration logic
│   └── KafkaProducerService    # Async event publishing with callback logging
└── messaging/consumer/
    └── KafkaConsumerService    # @KafkaListener methods
```

## Design Notes

### Orchestration vs. Choreography

This service implements **orchestration**: a single coordinator drives the workflow step by step. The alternative (choreography) has each service react to events independently. Orchestration was chosen for explicit control flow, easier debugging, and a clear place to implement compensation logic. See `docs/architecture/adr/0003-saga-orchestration.md` for the full rationale.

### Cross-Service Deserialization

The producer sets `ADD_TYPE_INFO_HEADERS = false` so consumers in other services (like the Order Service) can deserialize events by their method parameter type without needing classpath access to this service's classes.

### Step Interface (Phase 2)

The `SagaStep` interface is already defined:

```java
public interface SagaStep {
  String getName();
  String execute(SagaContext context);    // forward action
  String compensate(SagaContext context); // rollback action
}
```

Phase 2 will implement concrete steps (e.g., `ReserveInventoryStep`, `ChargePaymentStep`) and wire them into a reusable saga engine. The goal is to compare this custom engine against Camunda, Temporal, and Spring Statemachine.

## Related Services

| Service | Port | Role |
|---|---|---|
| **event-driven-order-service** | 8081 | Publishes `order.created`; consumes replies |
| Payment Service | — | Saga step (Phase 2) |
| Inventory Service | — | Saga step (Phase 2) |
| Notification Service | — | Saga step (Phase 2) |
