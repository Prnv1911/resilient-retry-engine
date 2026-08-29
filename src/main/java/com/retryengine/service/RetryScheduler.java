package com.retryengine.service;

import com.retryengine.model.NotificationTask;
import com.retryengine.model.TaskStatus;
import com.retryengine.repository.NotificationTaskRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class RetryScheduler {

    private final NotificationTaskRepository repository;
    private final TaskProcessor processor;

    // A Virtual Thread executor — each submitted task runs on its own Virtual Thread.
    // Creating millions of these is cheap because they are managed by the JVM, not the OS.
    // A traditional fixed thread pool (e.g., 10 threads) would bottleneck under load.
    private final ExecutorService virtualThreadExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    public RetryScheduler(NotificationTaskRepository repository, TaskProcessor processor) {
        this.repository = repository;
        this.processor = processor;
    }

    // Called once by Spring immediately after this bean is constructed.
    // Resets any tasks stuck in IN_PROGRESS from a previous crash — without this,
    // those rows would never be retried because the scheduler only picks up PENDING.
    // Safe to run here because the scheduler hasn't started ticking yet.
    @PostConstruct
    public void recoverOrphanedTasks() {
        List<NotificationTask> orphaned =
                repository.findAllByStatus(TaskStatus.IN_PROGRESS);

        if (orphaned.isEmpty()) return;

        orphaned.forEach(task -> task.setStatus(TaskStatus.PENDING));
        repository.saveAll(orphaned);

        System.out.println("[RECOVERY] Reset " + orphaned.size()
                + " orphaned IN_PROGRESS task(s) to PENDING on startup");
    }

    // fixedDelay = wait 2s AFTER the previous run completes before starting the next.
    // Safer than fixedRate, which fires every 2s regardless of how long the previous
    // run took — fixedRate can cause overlapping executions under load.
    @Scheduled(fixedDelay = 2000)
    public void processPendingTasks() {

        Instant now = Instant.now();

        List<NotificationTask> dueTasks =
                repository.findDueTasksByStatus(TaskStatus.PENDING, now);

        if (dueTasks.isEmpty()) {
            System.out.println("[SCHEDULER] No pending tasks at " + now);
            return;
        }

        System.out.println("[SCHEDULER] Found " + dueTasks.size() + " task(s) to process");

        // Each task gets its own Virtual Thread — they all run concurrently.
        // Sequential processing would mean task #2 waits for task #1 to finish,
        // including its network latency. Virtual Threads eliminate that bottleneck.
        for (NotificationTask task : dueTasks) {
            virtualThreadExecutor.submit(() -> processor.process(task));
        }
    }

    // Called by Spring automatically when the application is shutting down.
    // Stops accepting new tasks immediately, then waits up to 30s for any
    // in-progress virtual threads to finish their current task naturally.
    // WHY 30s: matches server.shutdown timeout in application.properties —
    // both must agree or one will cut off the other too early.
    // Without this, the JVM exits while tasks are mid-execution, leaving
    // rows stuck in IN_PROGRESS with no recovery path.
    @PreDestroy
    public void shutdown() {
        System.out.println("[SCHEDULER] Shutdown signal received — draining in-progress tasks...");
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                System.out.println("[SCHEDULER] Timeout reached — forcing shutdown");
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[SCHEDULER] Shutdown complete");
    }
}
