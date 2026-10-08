# Checkout con Saga orquestada sobre Spring Kafka

Checkout de una tienda online construido como **tres microservicios** que se coordinan **solo por mensajes**, sin transacciones distribuidas. Reservar el stock y cobrar viven en servicios y bases de datos distintas; si el cobro falla, el stock reservado se devuelve automáticamente (compensación).



| Capa | Tecnología |
|---|---|
| Lenguaje y framework | Java 21, Spring Boot 3.5, Maven multimódulo |
| Mensajería | Apache Kafka (KRaft) con **Spring for Apache Kafka 3.3** |
| order-service | Spring **WebFlux** + **R2DBC** (API y acceso a datos reactivos) |
| inventory-service | Spring MVC + JPA/Hibernate + *virtual threads* |
| payment-service | Spring MVC + JDBC (`JdbcClient`) |
| Base de datos | **PostgreSQL 17**, una base por servicio, migraciones con **Flyway** |
| Pruebas | JUnit 5, AssertJ, `@EmbeddedKafka`, H2 en modo PostgreSQL |
| Operación | Docker Compose, Dockerfile multi-stage, Spring Actuator, GitHub Actions |

## Arquitectura

```mermaid
flowchart LR
    C[Cliente] -->|"POST /api/ordenes<br/>Idempotency-Key"| O["order-service :8081<br/>WebFlux + R2DBC<br/>ORQUESTADOR"]
    O -->|outbox → relay| K1[(inventario.comandos)]
    K1 --> I["inventory-service :8082<br/>JPA + @KafkaHandler"]
    I --> K2[(inventario.respuestas)]
    K2 --> O
    O -->|outbox → relay| K3[(pagos.comandos)]
    K3 --> P["payment-service :8083<br/>@RetryableTopic"]
    P --> K4[(pagos.respuestas)]
    K4 --> O
    K3 -.reintentos.-> R[(pagos.comandos-retry-0/1/2)]
    R -.agotado.-> D[(pagos.comandos-dlt)]

    O --- DB1[("PostgreSQL<br/>ordenes")]
    I --- DB2[("PostgreSQL<br/>inventario")]
    P --- DB3[("PostgreSQL<br/>pagos")]
```


## Flujos de la saga

### Camino feliz

```mermaid
sequenceDiagram
    participant C as Cliente
    participant O as order-service
    participant I as inventory-service
    participant P as payment-service
    C->>O: POST /api/ordenes
    O->>O: guarda orden PENDIENTE_STOCK + ReservarStock en outbox (1 transacción)
    O-->>C: 202 Accepted (Location: /api/ordenes/{id})
    O->>I: ReservarStock (relay del outbox)
    I->>I: reserva stock + registra mensajeId (1 transacción)
    I->>O: StockReservado
    O->>O: PENDIENTE_PAGO + CobrarOrden en outbox
    O->>P: CobrarOrden
    P->>P: cobra en la pasarela y registra el pago
    P->>O: PagoAprobado
    O->>O: CONFIRMADA
```

### Compensación (pago rechazado)

```mermaid
sequenceDiagram
    participant O as order-service
    participant I as inventory-service
    participant P as payment-service
    O->>P: CobrarOrden (monto > 5000)
    P->>O: PagoRechazado("Fondos insuficientes")
    O->>O: COMPENSANDO + LiberarStock en outbox
    O->>I: LiberarStock
    I->>I: devuelve las unidades a "disponible"
    I->>O: StockLiberado
    O->>O: CANCELADA
```

## Requisitos

- Docker y Docker Compose v2
- JDK 21 y Maven 3.9+ (solo para compilar, correr los tests o desarrollar fuera de Docker)

## Ejecutar todo con Docker

```bash
docker compose up --build
```

| Servicio | URL |
|---|---|
| order-service | http://localhost:8081 |
| inventory-service | http://localhost:8082/api/stock |
| payment-service | http://localhost:8083/api/pagos/{ordenId} |
| Kafka UI | http://localhost:8090 |
| Adminer (perfil `tools`) | http://localhost:8085 |

```bash
docker compose --profile tools up -d    # además, Adminer para explorar las tablas
docker compose down                     # apaga y conserva los datos
docker compose down -v                  # apaga y borra los datos (BD y Kafka)
```




