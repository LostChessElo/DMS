package com.dms.api.dtos;

public record IdempotencyRecord(
        String requestHash,
        Status status
) {
    public enum Status {
        PROCESSING,
        SUCCEEDED
    }

    public IdempotencyRecord succeeded() {
        return new IdempotencyRecord(requestHash, Status.SUCCEEDED);
    }
}
