package com.retryengine.dto;

import com.retryengine.model.NotificationTask;
import com.retryengine.model.TaskStatus;

import java.time.Instant;

// Output DTO — controls exactly what fields the API exposes to callers.
// The NotificationTask entity has internal fields (version) that have no
// meaning outside the system and should never leak into the API contract.
public class TaskResponse {

    private final Long id;
    private final String recipient;
    private final String payload;
    private final TaskStatus status;
    private final int retryCount;
    private final int maxRetries;
    private final Instant nextRetryTime;
    private final String lastError;
    private final Instant createdAt;
    private final Instant updatedAt;

    // Static factory method — converts an entity to a response DTO.
    // Keeps the mapping logic here rather than scattered across controllers.
    public static TaskResponse from(NotificationTask task) {
        return new TaskResponse(task);
    }

    private TaskResponse(NotificationTask task) {
        this.id = task.getId();
        this.recipient = task.getRecipient();
        this.payload = task.getPayload();
        this.status = task.getStatus();
        this.retryCount = task.getRetryCount();
        this.maxRetries = task.getMaxRetries();
        this.nextRetryTime = task.getNextRetryTime();
        this.lastError = task.getLastError();
        this.createdAt = task.getCreatedAt();
        this.updatedAt = task.getUpdatedAt();
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getPayload() { return payload; }
    public TaskStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getNextRetryTime() { return nextRetryTime; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
