package com.dms.api.controllers;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.dtos.SubmissionResponse;
import com.dms.api.service.EnquiryMailService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;

@RestController
@RequestMapping("api/v1/enquire")
public class EnquiryMailController {
    private final EnquiryMailService service;

    public EnquiryMailController(EnquiryMailService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<SubmissionResponse> email(@RequestBody @Valid FormSubmission submission) {
        return new ResponseEntity<>(new SubmissionResponse("done"), HttpStatus.OK);
    }

}
