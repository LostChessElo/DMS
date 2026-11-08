package com.dms.api.controllers;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.dtos.SubmissionResponse;
import com.dms.api.service.EnquiryMailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enquiries")
public class EnquiryMailController {
    private final EnquiryMailService service;

    public EnquiryMailController(EnquiryMailService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> email(@RequestBody @Valid FormSubmission submission) {
        service.send(submission);
        SubmissionResponse response = new SubmissionResponse("Thank you for the email, we'll get back to you shortly!");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
