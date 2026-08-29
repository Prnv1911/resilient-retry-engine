package com.retryengine.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateTaskRequest {

    @NotBlank(message = "Recipient must not be blank")
    @Email(message = "Recipient must be a valid email address")
    private String recipient;

    @NotBlank(message = "Payload must not be blank")
    @Size(max = 10_000, message = "Payload must not exceed 10,000 characters")
    private String payload;

    // Optional — defaults to 5 in NotificationTask constructor if not provided.
    // Allows callers to tune retry behaviour per task: a compliance notification
    // might set 10, a low-priority marketing email might set 2.
    @Min(value = 1, message = "maxRetries must be at least 1")
    @Max(value = 20, message = "maxRetries must not exceed 20")
    private Integer maxRetries;

    public CreateTaskRequest() {}

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
}
