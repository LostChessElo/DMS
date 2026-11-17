package com.dms.api.service;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.dtos.IdempotencyRecord;
import com.dms.api.exception.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.hash.Funnel;
import com.google.common.hash.Hashing;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final Cache<String, IdempotencyRecord> cache;
    private final EnquiryMailService mailService;

    public IdempotencyService(Cache<String, IdempotencyRecord> cache, EnquiryMailService mailService) {
        this.cache = cache;
        this.mailService = mailService;
    }

    public void process(String key, FormSubmission submission) {
        String hash = hash(submission);
        IdempotencyRecord reserve = new IdempotencyRecord(hash, IdempotencyRecord.Status.PROCESSING);

        Optional<IdempotencyRecord> entry = Optional.ofNullable(cache.asMap().putIfAbsent(key, reserve));

        if (entry.isPresent()) {
            if (!entry.get().requestHash().equals(hash)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Key already in use.");
            }
            if (entry.get().status() == IdempotencyRecord.Status.PROCESSING) {
                throw new ApiException(HttpStatus.CONFLICT, "Request being processed.");
            }

            return;
        }

        try {
            mailService.send(submission);
            cache.asMap().replace(key, reserve, reserve.succeeded());
        } catch (RuntimeException e) {
            cache.asMap().remove(key, reserve);
            throw e;
        }
    }

    private String hash(FormSubmission submission) {
        Funnel<FormSubmission> SUBMISSION_FUNNEL = (sub, into) -> {
            into.putString(sub.name(), StandardCharsets.UTF_8);
            into.putString(sub.email(), StandardCharsets.UTF_8);
            into.putString(sub.message(), StandardCharsets.UTF_8);
            into.putBoolean(sub.consent());
        };

        return Hashing.sha256()
                .newHasher()
                .putObject(submission, SUBMISSION_FUNNEL)
                .hash()
                .toString();
    }
}
