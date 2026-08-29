# Resilient Retry Engine

A lightweight Spring Boot service that accepts notification tasks and reliably delivers them to external systems using retry scheduling, exponential backoff with jitter, and safe concurrency patterns.

## Key features

- Persistent task queue backed by PostgreSQL (JPA/Hibernate)
- Scheduler that picks due tasks and processes them concurrently using Java Virtual Threads
- Safe processing using pessimistic row locks and transactional updates
- Exponential backoff with jitter and configurable max retries
- Recovery on startup for tasks left in IN_PROGRESS
- HTTP API for creating, listing, inspecting, retrying, and getting statistics for tasks
- Test-friendly with H2 in tests and Spring Boot Test support

## High-level architecture

- Controller layer (NotificationTaskController) exposes REST endpoints for task lifecycle and stats.
- Repository layer (NotificationTaskRepository) uses JPA with custom queries and PESSIMISTIC_WRITE locking for safe claim semantics.
- Domain model (NotificationTask) stores recipient, payload, retry metadata, status, timestamps, and optimistic versioning.
- Scheduler (RetryScheduler) periodically selects due PENDING tasks and delegates processing to TaskProcessor using a Virtual Thread executor.
- Processor (TaskProcessor) re-checks and locks a single task, marks it IN_PROGRESS, calls NotificationSender, and updates state (SUCCESS, PENDING with nextRetryTime, or FAILED).
- NotificationSender abstracts delivery; in this sample it simulates failures to exercise retry logic.

Design notes

- PESSIMISTIC_WRITE prevents multiple nodes from processing the same task simultaneously.
- The system resets IN_PROGRESS rows on startup to avoid stuck tasks after crashes.
- Virtual Threads (Java 21+) allow high concurrency without creating many OS threads.
- Compound DB index on (status, next_retry_time) optimizes scheduler queries over large tables.

## Prerequisites

- Java 21+ (project uses Virtual Threads)
- Maven 3.6+
- PostgreSQL (or use Docker for a local instance)

Optional for tests

- No local DB is required for tests — the test profile uses H2 in-memory database.

## Configuration

Relevant properties: src/main/resources/application.properties

- spring.datasource.url (default: jdbc:postgresql://localhost:5432/retrydb)
- spring.datasource.username, spring.datasource.password
- server.port (default: 8080)
- spring.jpa.hibernate.ddl-auto (set to `update` in this sample; change for production)
- server.shutdown and graceful shutdown timeouts are configured for clean draining

## Installation and running (local)

1. Start Postgres (example using Docker):

   docker run --name retry-db -e POSTGRES_PASSWORD=secret -e POSTGRES_DB=retrydb -p 5432:5432 -d postgres

2. Build and run the service:

   mvn -DskipTests package
   mvn spring-boot:run

The service listens on http://localhost:8080 by default.

## API examples

Create a task (POST /api/v1/tasks)

curl -s -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d '{"recipient":"user@example.com","payload":"{\"subject\":\"Hello\",\"body\":\"Hi\"}","maxRetries":5}'

Response: 201 Created with Location header and TaskResponse JSON.

Get a task (GET /api/v1/tasks/{id})

curl http://localhost:8080/api/v1/tasks/1

List tasks (paginated, optional status filter)

curl "http://localhost:8080/api/v1/tasks?page=0&size=20&sort=createdAt,desc"

Get failed tasks (dead-letter)

curl http://localhost:8080/api/v1/tasks/failed

Get stats

curl http://localhost:8080/api/v1/tasks/stats

Manually retry a failed task

curl -X POST http://localhost:8080/api/v1/tasks/123/retry

## Data model highlights

NotificationTask fields:
- id, recipient, payload
- status (PENDING, IN_PROGRESS, SUCCESS, FAILED)
- retryCount, maxRetries, nextRetryTime, lastError
- createdAt, updatedAt

Indexes and ID allocation are tuned for performance (sequence with allocationSize and a compound index on status + next_retry_time).

## Testing

Run unit/integration tests with Maven (H2 in-memory DB for tests):

mvn test

There is an integration test that exercises TaskProcessor behavior under controlled conditions.

## Production considerations

- Use a managed Postgres or hardened instance and do NOT use `spring.jpa.hibernate.ddl-auto=update` for production without review.
- Replace the NotificationSender simulation with a robust delivery client (retryable HTTP client, idempotency, observability).
- Configure observability (metrics, tracing) and add circuit-breakers if the downstream service can cause long outages.
- Tune virtual thread and JVM flags according to workload, and validate on target Java runtime.

## Contributing

- Clone the repository, open in your IDE, and run tests locally.
- Follow the existing patterns for repository queries, transactional boundaries, and scheduler semantics.

## License

See LICENSE (if present) or add licensing terms before publishing.

---

Project generated from the codebase present in this repository.
