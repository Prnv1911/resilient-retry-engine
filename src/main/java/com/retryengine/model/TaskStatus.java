package com.retryengine.model;

public enum TaskStatus {

    // Task has been accepted and is waiting to be picked up by the scheduler
    PENDING,

    // Scheduler has claimed this task — prevents double-pickup across nodes.
    // Without this state, two scheduler threads waking up simultaneously could
    // both see the same PENDING row and process it twice.
    IN_PROGRESS,

    // Terminal success state — scheduler will never touch this row again
    SUCCESS,

    // Terminal failure state — retryCount has reached maxRetries
    FAILED
}
