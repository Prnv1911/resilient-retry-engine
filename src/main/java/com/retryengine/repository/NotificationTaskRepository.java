package com.retryengine.repository;

import com.retryengine.model.NotificationTask;
import com.retryengine.model.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {

    // Custom JPQL query because we need OR logic (null OR before threshold).
    // Spring's method name parser cannot express OR conditions.
    @Query("SELECT t FROM NotificationTask t WHERE t.status = :status " +
           "AND (t.nextRetryTime IS NULL OR t.nextRetryTime <= :threshold)")
    List<NotificationTask> findDueTasksByStatus(
            @Param("status") TaskStatus status,
            @Param("threshold") Instant threshold);

    // PESSIMISTIC_WRITE translates to SELECT ... FOR UPDATE in SQL.
    // Locks the row at read time — only one node can hold this lock at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM NotificationTask t WHERE t.id = :id")
    Optional<NotificationTask> findByIdWithLock(@Param("id") Long id);

    // Used on startup to recover tasks orphaned in IN_PROGRESS by a previous crash.
    @Query("SELECT t FROM NotificationTask t WHERE t.status = :status")
    List<NotificationTask> findAllByStatus(@Param("status") TaskStatus status);

    // Paginated list filterable by status — powers GET /api/v1/tasks?status=FAILED&page=0&size=20.
    // Spring MVC injects Pageable automatically from request query params.
    // Page<> includes total count, total pages, and the current slice of results.
    Page<NotificationTask> findByStatus(TaskStatus status, Pageable pageable);

    // Aggregates task counts grouped by status — powers GET /api/v1/tasks/stats.
    // Returns [status, count] pairs which the controller maps into a readable response.
    @Query("SELECT t.status, COUNT(t) FROM NotificationTask t GROUP BY t.status")
    List<Object[]> countGroupedByStatus();
}
