package com.dms.api.exception;

import org.springframework.http.ResponseEntity;
import com.dms.api.dtos.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        ErrorResponse errorResponse = new ErrorResponse(exception.getStatus().value(), exception.getMessage(), Instant.now());
        return new ResponseEntity<>(errorResponse, exception.getStatus());
    }

}
