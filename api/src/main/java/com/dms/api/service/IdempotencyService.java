package com.dms.api.service;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.dtos.IdempotencyRecord;
import com.dms.api.exception.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final Cache<UUID, IdempotencyRecord> cache;
    private final EnquiryMailService mailService;

    public IdempotencyService(Cache<UUID, IdempotencyRecord> cache, EnquiryMailService mailService) {
        this.cache = cache;
        this.mailService = mailService;
    }

    public void process(UUID key, FormSubmission submission) {
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


    private static String hash(FormSubmission s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, s.name());
            update(digest, s.email());
            update(digest, s.message());
            digest.update((byte) (s.consent() ? 1 : 0));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every JVM must provide SHA-256
        }
    }

    private static void update(MessageDigest digest, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
