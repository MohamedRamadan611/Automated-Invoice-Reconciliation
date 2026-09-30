package com.agent.reconciliation.domain.dto;

import java.time.LocalDateTime;

/**
 * Standard structured API error response record.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) {}
