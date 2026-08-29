package com.retryengine.service;

import com.retryengine.model.NotificationTask;
import org.springframework.stereotype.Service;

@Service
public class NotificationSender {

    // Simulates one attempt to deliver a notification.
    // In production, replace this body with a real API call (SendGrid, AWS SES, Twilio).
    // The retry logic in RetryScheduler does not change — only this method changes.
    public void send(NotificationTask task) {

        // Simulate network latency — real API calls take time.
        // This also makes Virtual Thread behaviour realistic: the thread parks
        // during sleep, freeing the underlying OS thread for other work.
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Send interrupted", e);
        }

        // 60% failure rate simulates a flaky external service.
        // The caller (RetryScheduler) catches this exception and handles retry logic.
        // This class has zero knowledge of retries — that separation is intentional.
        if (Math.random() < 0.6) {
            throw new RuntimeException(
                "Simulated failure: email provider unreachable for " + task.getRecipient()
            );
        }

        System.out.println("[SENT] Email delivered to: " + task.getRecipient()
            + " | Task ID: " + task.getId());
    }
}
