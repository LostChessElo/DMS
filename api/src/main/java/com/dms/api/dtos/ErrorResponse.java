package com.dms.api.dtos;

import java.time.Instant;

public record ErrorResponse(
        int status,
        String message,
        Instant timeStamp
) {
}
