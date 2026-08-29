package com.retryengine.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
// Explicit table name avoids Hibernate's case-sensitive default naming surprises
@Table(
    name = "notification_tasks",
    indexes = {
        // The scheduler queries WHERE status = 'PENDING' AND next_retry_time <= NOW()
        // on every tick. This compound index lets Postgres jump directly to matching
        // rows instead of scanning the entire table — critical at 100k+ rows.
        @Index(name = "idx_status_next_retry", columnList = "status, next_retry_time")
    }
)
public class NotificationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "task_seq")
    @SequenceGenerator(name = "task_seq", sequenceName = "task_sequence", allocationSize = 50)
    // allocationSize=50 means Hibernate reserves 50 IDs in one DB round-trip and
    // allocates them in memory. Under high insert load this cuts DB calls by 50x
    // compared to IDENTITY strategy which requires a round-trip per row.
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    // STRING not ORDINAL — if you reorder the enum, ORDINAL silently maps to wrong values
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(nullable = false)
    private int maxRetries = 5;

    // null means "ready to run immediately"
    // A future Instant means "don't touch until this time passes"
    private Instant nextRetryTime;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // Hibernate increments this on every UPDATE. If two threads read the same row
    // and both try to update it, the second one gets OptimisticLockException because
    // the version has already changed. Phase 3 adds PESSIMISTIC_WRITE on top.
    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = TaskStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    protected NotificationTask() {
        // Required by JPA — it needs a no-arg constructor to instantiate entities via reflection
    }

    public NotificationTask(String recipient, String payload) {
        this.recipient = recipient;
        this.payload = payload;
        this.status = TaskStatus.PENDING;
    }

    // Overloaded constructor for callers that specify a custom retry limit.
    // maxRetries field defaults to 5 in the field declaration — this overrides it.
    public NotificationTask(String recipient, String payload, int maxRetries) {
        this(recipient, payload);
        this.maxRetries = maxRetries;
    }

    public Long getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getPayload() { return payload; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(Instant nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
