package com.dms.api.controllers;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.dtos.SubmissionResponse;
import com.dms.api.service.IdempotencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enquiries")
public class EnquiryMailController {
    private final IdempotencyService service;

    public EnquiryMailController(IdempotencyService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> email(
            @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody @Valid FormSubmission submission)
    {
        service.process(key, submission);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubmissionResponse(
                        "Thank you for the email, we'll get back to you shortly!"
                ));
    }
}
