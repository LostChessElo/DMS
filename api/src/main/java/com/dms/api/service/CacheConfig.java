package com.dms.api.service;

import com.dms.api.dtos.IdempotencyRecord;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, IdempotencyRecord> indempotencyCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(10_000)
                .build();
    }
}
