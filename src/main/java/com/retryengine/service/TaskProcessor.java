package com.retryengine.service;

import com.retryengine.model.NotificationTask;
import com.retryengine.model.TaskStatus;
import com.retryengine.repository.NotificationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TaskProcessor {

    private final NotificationTaskRepository repository;
    private final NotificationSender sender;

    public TaskProcessor(NotificationTaskRepository repository, NotificationSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    // @Transactional works here because Spring calls this method through its proxy —
    // TaskProcessor is a separate bean injected into RetryScheduler.
    // If this method lived in RetryScheduler and was called via `this.processOneTask()`,
    // Spring's proxy would be bypassed and @Transactional would be silently ignored.
    @Transactional
    public void process(NotificationTask candidate) {

        // Re-fetch with PESSIMISTIC_WRITE lock — only one node can hold this lock
        // at a time. Any other node trying to process the same task blocks here
        // until the transaction commits, then sees IN_PROGRESS and returns.
        NotificationTask task = repository.findByIdWithLock(candidate.getId())
                .orElse(null);

        if (task == null || task.getStatus() != TaskStatus.PENDING) {
            return;
        }

        task.setStatus(TaskStatus.IN_PROGRESS);
        repository.save(task);

        try {
            sender.send(task);

            task.setStatus(TaskStatus.SUCCESS);
            task.setLastError(null);
            repository.save(task);

            System.out.println("[SUCCESS] Task " + task.getId() + " completed");

        } catch (Exception e) {

            int attempts = task.getRetryCount() + 1;
            task.setRetryCount(attempts);
            task.setLastError(e.getMessage());

            if (attempts >= task.getMaxRetries()) {
                task.setStatus(TaskStatus.FAILED);
                System.out.println("[FAILED] Task " + task.getId()
                    + " exhausted all " + task.getMaxRetries() + " retries");
            } else {
                // Exponential backoff + jitter: 2^attempts seconds + random 0-999ms.
                // Jitter prevents a thundering herd — tasks that fail simultaneously
                // are spread out randomly instead of all retrying at the same instant.
                long backoffSeconds = (long) Math.pow(2, attempts);
                long jitterMillis = (long) (Math.random() * 1000);
                task.setNextRetryTime(Instant.now().plusSeconds(backoffSeconds).plusMillis(jitterMillis));
                task.setStatus(TaskStatus.PENDING);

                System.out.println("[RETRY] Task " + task.getId()
                    + " will retry in " + backoffSeconds + "s (attempt " + attempts + "/"
                    + task.getMaxRetries() + ")");
            }

            repository.save(task);
        }
    }
}
