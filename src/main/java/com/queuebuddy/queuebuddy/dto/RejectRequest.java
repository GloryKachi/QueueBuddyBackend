package com.queuebuddy.queuebuddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "Please give the patient a short reason")
        @Size(max = 200, message = "Keep the reason under 200 characters") String reason) {}