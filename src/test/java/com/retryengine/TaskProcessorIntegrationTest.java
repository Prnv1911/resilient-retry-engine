package com.retryengine;

import com.retryengine.model.NotificationTask;
import com.retryengine.model.TaskStatus;
import com.retryengine.repository.NotificationTaskRepository;
import com.retryengine.service.NotificationSender;
import com.retryengine.service.TaskProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

// @SpringBootTest boots the full Spring application context — real beans, real DB queries.
// This is an integration test, not a unit test. We want to verify the actual retry logic
// including @Transactional, the PESSIMISTIC_WRITE lock, and the real repository queries.
@SpringBootTest
class TaskProcessorIntegrationTest {

    @Autowired
    private TaskProcessor processor;

    @Autowired
    private NotificationTaskRepository repository;

    // MockBean replaces the real NotificationSender with a mock we control.
    // WHY mock only this: we want to control success/failure without real network calls.
    // Everything else (DB, transactions, locks) is real.
    @MockBean
    private NotificationSender sender;

    // Wipe the database before each test so tests don't interfere with each other.
    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void task_succeeds_on_first_attempt() {
        // ARRANGE — sender does nothing = no exception = success
        doNothing().when(sender).send(any());

        NotificationTask task = repository.save(new NotificationTask("user@example.com", "Hello"));

        // ACT
        processor.process(task);

        // ASSERT — reload from DB to see what was actually persisted
        NotificationTask result = repository.findById(task.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.SUCCESS);
        assertThat(result.getRetryCount()).isEqualTo(0);
        assertThat(result.getLastError()).isNull();
    }

    @Test
    void task_is_rescheduled_after_first_failure() {
        // ARRANGE — sender always throws
        doThrow(new RuntimeException("Connection timeout"))
                .when(sender).send(any());

        NotificationTask task = repository.save(new NotificationTask("user@example.com", "Hello"));

        // ACT
        processor.process(task);

        // ASSERT
        NotificationTask result = repository.findById(task.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getLastError()).isEqualTo("Connection timeout");
        // nextRetryTime must be in the future — task is waiting for backoff to expire
        assertThat(result.getNextRetryTime()).isAfter(Instant.now());
    }

    @Test
    void task_fails_permanently_after_max_retries() {
        // ARRANGE — sender always fails
        doThrow(new RuntimeException("Service unavailable"))
                .when(sender).send(any());

        NotificationTask task = repository.save(new NotificationTask("user@example.com", "Hello"));

        // ACT — simulate exhausting all retries
        for (int i = 0; i < task.getMaxRetries(); i++) {
            // Reload the task each iteration — status was set back to PENDING after each failure.
            // Force PENDING so processor doesn't skip it (it checks status before processing).
            NotificationTask current = repository.findById(task.getId()).orElseThrow();
            current.setStatus(TaskStatus.PENDING);
            repository.save(current);
            processor.process(current);
        }

        // ASSERT
        NotificationTask result = repository.findById(task.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.getRetryCount()).isEqualTo(task.getMaxRetries());
    }
}
