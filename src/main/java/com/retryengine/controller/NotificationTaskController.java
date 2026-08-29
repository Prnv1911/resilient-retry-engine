package com.retryengine.controller;

import com.retryengine.dto.CreateTaskRequest;
import com.retryengine.dto.TaskResponse;
import com.retryengine.model.NotificationTask;
import com.retryengine.model.TaskStatus;
import com.retryengine.repository.NotificationTaskRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
public class NotificationTaskController {

    private final NotificationTaskRepository repository;

    public NotificationTaskController(NotificationTaskRepository repository) {
        this.repository = repository;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request) {

        // Use the overloaded constructor only if caller provided maxRetries.
        // Otherwise default to 5 — the standard constructor handles that.
        NotificationTask task = (request.getMaxRetries() != null)
                ? new NotificationTask(request.getRecipient(), request.getPayload(), request.getMaxRetries())
                : new NotificationTask(request.getRecipient(), request.getPayload());

        NotificationTask saved = repository.save(task);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();

        return ResponseEntity.created(location).body(TaskResponse.from(saved));
    }

    // ── Read single ───────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long id) {
        return repository.findById(id)
                .map(TaskResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Read list (paginated, filterable by status) ───────────────────────────
    // GET /api/v1/tasks                        → all tasks, page 0, size 20
    // GET /api/v1/tasks?status=FAILED          → only failed tasks
    // GET /api/v1/tasks?page=1&size=10         → second page, 10 per page
    // GET /api/v1/tasks?sort=createdAt,desc    → sorted by creation time

    @GetMapping
    public Page<TaskResponse> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (status != null) {
            return repository.findByStatus(status, pageable).map(TaskResponse::from);
        }
        return repository.findAll(pageable).map(TaskResponse::from);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    // GET /api/v1/tasks/stats
    // → { "PENDING": 12, "IN_PROGRESS": 3, "SUCCESS": 847, "FAILED": 5 }

    @GetMapping("/stats")
    public Map<String, Long> getStats() {
        // Seed all statuses at zero so the response always has all four keys
        // even when no tasks exist for a given status.
        Map<String, Long> result = new java.util.LinkedHashMap<>();
        for (TaskStatus s : TaskStatus.values()) result.put(s.name(), 0L);

        repository.countGroupedByStatus()
                .forEach(row -> result.put(((TaskStatus) row[0]).name(), (Long) row[1]));

        return result;
    }

    // ── Dead letter ───────────────────────────────────────────────────────────
    // GET /api/v1/tasks/failed → shorthand for ?status=FAILED, page 0

    @GetMapping("/failed")
    public List<TaskResponse> getFailedTasks() {
        return repository.findAllByStatus(TaskStatus.FAILED)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    // POST /api/v1/tasks/{id}/retry → requeue a FAILED task back to PENDING
    // Idempotent: calling it twice on an already-PENDING task is a no-op.

    @PostMapping("/{id}/retry")
    public ResponseEntity<TaskResponse> retryTask(@PathVariable Long id) {
        return repository.findById(id)
                .map(task -> {
                    if (task.getStatus() != TaskStatus.FAILED) {
                        // Only FAILED tasks can be manually requeued
                        return ResponseEntity.badRequest().<TaskResponse>build();
                    }
                    task.setStatus(TaskStatus.PENDING);
                    task.setNextRetryTime(null);   // pick up immediately on next tick
                    task.setLastError(null);
                    return ResponseEntity.ok(TaskResponse.from(repository.save(task)));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
