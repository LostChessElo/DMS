package com.dms.api;

import com.dms.api.dtos.FormSubmission;
import com.dms.api.dtos.IdempotencyRecord;
import com.dms.api.exception.ApiException;
import com.dms.api.service.EnquiryMailService;
import com.dms.api.service.IdempotencyService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyTests {

    private static final String NAME = "TestBot";
    private static final String EMAIL = "testbot@testing.com";
    private static final String MESSAGE = "Hello, I would like a quote for testing agencies";
    private static final Duration WINDOW = Duration.ofSeconds(30);

    @Mock
    EnquiryMailService mailService;

    private Cache<UUID, IdempotencyRecord> cache;
    private IdempotencyService service;

    // cache with a 30 second window and a clock the tests control
    private FakeTicker ticker;
    private Cache<UUID, IdempotencyRecord> timedCache;
    private IdempotencyService timedService;

    private ExecutorService executor;

    @BeforeEach
    public void setup() {
        cache = Caffeine.newBuilder().build();
        service = new IdempotencyService(cache, mailService);

        ticker = new FakeTicker();
        timedCache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfterWrite(WINDOW)
                .executor(Runnable::run)
                .build();
        timedService = new IdempotencyService(timedCache, mailService);

        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    public void teardown() {
        executor.shutdownNow();
    }

    private FormSubmission submission() {
        return new FormSubmission(NAME, EMAIL, MESSAGE, true);
    }

    private FormSubmission differentSubmission() {
        return new FormSubmission("Someone Else", "else@example.org", "A different enquiry entirely", true);
    }

    static class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }

    @Nested
    @DisplayName("Happy path cases")
    class NormalPathTests {

        @Test
        void validRequestReturns201AndIsPresentInCache() {
            UUID key = UUID.randomUUID();
            service.process(key, submission());

            IdempotencyRecord result = cache.getIfPresent(key);
            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);
            assertThat(result.requestHash()).isNotBlank();
        }

        @Test
        void recordInTransitStatusIsProcessing() {
            UUID key = UUID.randomUUID();
            AtomicReference<IdempotencyRecord> seenDuringSend = new AtomicReference<>();

            doAnswer(inv -> {
                seenDuringSend.set(cache.getIfPresent(key));
                return null;
            }).when(mailService).send(any(FormSubmission.class));

            service.process(key, submission());

            IdempotencyRecord during = seenDuringSend.get();
            assertThat(during).isNotNull();
            assertThat(during.status()).isEqualTo(IdempotencyRecord.Status.PROCESSING);
            assertThat(during.requestHash()).isNotBlank();

            IdempotencyRecord after = cache.getIfPresent(key);
            assertThat(after).isNotNull();
            assertThat(after.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);
            assertThat(after.requestHash()).isEqualTo(during.requestHash());
        }

    }

    @Nested
    @DisplayName("Replay cases")
    class Replay {

        @Test
        void sameSubmissionTwiceSendsOnce() {
            UUID key = UUID.randomUUID();
            FormSubmission submission = submission();

            service.process(key, submission);
            IdempotencyRecord sentRecord = cache.getIfPresent(key);
            assertThat(sentRecord).isNotNull();
            assertThat(sentRecord.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);
            assertThat(sentRecord.requestHash()).isNotBlank();

            service.process(key, submission);
            service.process(key, submission);
            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

        @Test
        void replayKeepsTheOriginalRecordUnchanged() {
            UUID key = UUID.randomUUID();
            service.process(key, submission());
            IdempotencyRecord original = cache.getIfPresent(key);

            service.process(key, submission());

            assertThat(cache.getIfPresent(key)).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("Payload mismatch cases")
    class PayloadMismatch {

        @Test
        void payloadMismatchThrows422() {
            UUID key = UUID.randomUUID();
            service.process(key, submission());
            IdempotencyRecord sent = cache.getIfPresent(key);

            FormSubmission mismatch = new FormSubmission(
                    "Miss missmatch",
                    "missmatch@error.org",
                    "Im trying to break your API.",
                    true
            );

            assertThatExceptionOfType(ApiException.class)
                    .isThrownBy(() -> service.process(key, mismatch))
                    .extracting(ApiException::getStatus)
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

            assertThat(cache.getIfPresent(key)).isEqualTo(sent);
            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

        static Stream<Arguments> submissionsDifferingInOneField() {
            return Stream.of(
                    Arguments.of("name", new FormSubmission("Other Name", EMAIL, MESSAGE, true)),
                    Arguments.of("email", new FormSubmission(NAME, "other@testing.com", MESSAGE, true)),
                    Arguments.of("message", new FormSubmission(NAME, EMAIL, MESSAGE + " (edited)", true)),
                    Arguments.of("consent", new FormSubmission(NAME, EMAIL, MESSAGE, false))
            );
        }

        @ParameterizedTest(name = "changing only the {0} gives 422")
        @MethodSource("submissionsDifferingInOneField")
        void everyFieldIsPartOfTheHash(String changedField, FormSubmission changed) {
            UUID key = UUID.randomUUID();
            service.process(key, submission());
            IdempotencyRecord sent = cache.getIfPresent(key);

            assertThatExceptionOfType(ApiException.class)
                    .isThrownBy(() -> service.process(key, changed))
                    .extracting(ApiException::getStatus)
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

            assertThat(cache.getIfPresent(key)).isEqualTo(sent);
            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

        @Test
        void fieldBoundaryMismatchThrows422() {
            UUID key = UUID.randomUUID();

            // no delimeter
            FormSubmission first = new FormSubmission("ab", "c@x.co", "...", true);
            FormSubmission mismatch = new FormSubmission("a", "bc@x.co", "...", true);

            service.process(key, first);
            IdempotencyRecord sent = cache.getIfPresent(key);

            assertThatExceptionOfType(ApiException.class)
                    .isThrownBy(() -> service.process(key, mismatch))
                    .extracting(ApiException::getStatus)
                    .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

            assertThat(cache.getIfPresent(key)).isEqualTo(sent);
            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

    }

    @Nested
    @DisplayName("Request in transit cases")
    class RequestInTransit {

        @Test
        void sentWhileRequestInTransitThrows409() {
            UUID key = UUID.randomUUID();

            doAnswer(inv -> {
                assertThatExceptionOfType(ApiException.class)
                        .isThrownBy(() -> service.process(key, submission()))
                        .extracting(ApiException::getStatus)
                        .isEqualTo(HttpStatus.CONFLICT);
                return null;
            }).when(mailService).send(any(FormSubmission.class));

            service.process(key, submission());

            IdempotencyRecord sent = cache.getIfPresent(key);
            assertThat(sent).isNotNull();
            assertThat(sent.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);

            assertThatCode(() -> service.process(key, submission())).doesNotThrowAnyException();

            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

        @Test
        void mismatchedPayloadWhileInFlightThrows422NotConflict() {
            UUID key = UUID.randomUUID();

            doAnswer(inv -> {
                assertThatExceptionOfType(ApiException.class)
                        .isThrownBy(() -> service.process(key, differentSubmission()))
                        .extracting(ApiException::getStatus)
                        .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                return null;
            }).when(mailService).send(any(FormSubmission.class));

            service.process(key, submission());

            verify(mailService, times(1)).send(any(FormSubmission.class));
        }
    }

    @Nested
    @DisplayName("Independent cases")
    class IndependentRequests {

        @Test
        void samePayloadDifferentKeyReturns201() {
            UUID key1 = UUID.randomUUID();
            UUID key2 = UUID.randomUUID();
            service.process(key1, submission());
            service.process(key2, submission());

            IdempotencyRecord first = cache.getIfPresent(key1);
            IdempotencyRecord second = cache.getIfPresent(key2);

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.requestHash()).isEqualTo(second.requestHash());

            verify(mailService, times(2)).send(any(FormSubmission.class));
        }

        @Test
        void differentKeysWithDifferentPayloadsAreIndependent() {
            UUID key1 = UUID.randomUUID();
            UUID key2 = UUID.randomUUID();

            service.process(key1, submission());
            service.process(key2, differentSubmission());

            IdempotencyRecord first = cache.getIfPresent(key1);
            IdempotencyRecord second = cache.getIfPresent(key2);

            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(first.requestHash()).isNotEqualTo(second.requestHash());
            assertThat(first.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);
            assertThat(second.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);

            verify(mailService, times(2)).send(any(FormSubmission.class));
        }

    }

    @Nested
    @DisplayName("Failure cases")
    class Failure {

        @Test
        void failedSendPropagatesTheSameExceptionAndReleasesTheKey() {
            UUID key = UUID.randomUUID();
            MailSendException failure = new MailSendException("smtp down");
            doThrow(failure).when(mailService).send(any(FormSubmission.class));

            assertThatThrownBy(() -> service.process(key, submission()))
                    .isSameAs(failure);

            assertThat(cache.getIfPresent(key)).isNull();
        }

        static Stream<RuntimeException> sendFailures() {
            return Stream.of(
                    new MailSendException("smtp unreachable"),
                    new MailAuthenticationException("bad credentials"),
                    new MailParseException("bad address"),
                    new IllegalStateException("something unexpected")
            );
        }

        @ParameterizedTest
        @MethodSource("sendFailures")
        void anyFailureReleasesTheKey(RuntimeException failure) {
            UUID key = UUID.randomUUID();
            doThrow(failure).when(mailService).send(any(FormSubmission.class));

            assertThatThrownBy(() -> service.process(key, submission()))
                    .isSameAs(failure);

            assertThat(cache.getIfPresent(key)).isNull();
        }

        @Test
        void retryWithTheSameKeyAfterAFailureSendsAndSucceeds() {
            UUID key = UUID.randomUUID();
            doThrow(new MailSendException("smtp down"))
                    .doNothing()
                    .when(mailService).send(any(FormSubmission.class));

            assertThatThrownBy(() -> service.process(key, submission()))
                    .isInstanceOf(MailSendException.class);

            assertThatCode(() -> service.process(key, submission())).doesNotThrowAnyException();

            IdempotencyRecord record = cache.getIfPresent(key);
            assertThat(record).isNotNull();
            assertThat(record.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);
            verify(mailService, times(2)).send(any(FormSubmission.class));
        }

        @Test
        void aFailureOnOneKeyDoesNotDisturbAnotherKeysRecord() {
            UUID goodKey = UUID.randomUUID();
            UUID badKey = UUID.randomUUID();

            service.process(goodKey, submission());
            IdempotencyRecord goodRecord = cache.getIfPresent(goodKey);

            doThrow(new MailSendException("smtp down")).when(mailService).send(any(FormSubmission.class));
            assertThatThrownBy(() -> service.process(badKey, differentSubmission()))
                    .isInstanceOf(MailSendException.class);

            assertThat(cache.getIfPresent(goodKey)).isEqualTo(goodRecord);
            assertThat(cache.getIfPresent(badKey)).isNull();
        }
    }

    @Nested
    @DisplayName("Time boxed cases")
    class TimeWindow {

        @Test
        void sameKeyStillReplaysInsideTheWindow() {
            UUID key = UUID.randomUUID();
            timedService.process(key, submission());

            ticker.advance(Duration.ofSeconds(29));
            timedService.process(key, submission());

            assertThat(timedCache.getIfPresent(key)).isNotNull();
            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

        @Test
        void sameKeyIsProcessedAgainOnceTheWindowHasPassed() {
            UUID key = UUID.randomUUID();
            timedService.process(key, submission());

            ticker.advance(Duration.ofSeconds(31));
            assertThat(timedCache.getIfPresent(key)).isNull();

            timedService.process(key, submission());

            verify(mailService, times(2)).send(any(FormSubmission.class));
        }

        @Test
        void aDifferentPayloadIsAcceptedOnceTheWindowHasPassed() {
            UUID key = UUID.randomUUID();
            timedService.process(key, submission());

            ticker.advance(Duration.ofSeconds(31));

            assertThatCode(() -> timedService.process(key, differentSubmission()))
                    .doesNotThrowAnyException();
            verify(mailService, times(2)).send(any(FormSubmission.class));
        }

        @Test
        void theWindowIsCountedFromCompletionNotFromTheClaim() {
            UUID key = UUID.randomUUID();

            doAnswer(inv -> {
                ticker.advance(Duration.ofSeconds(25));
                return null;
            }).when(mailService).send(any(FormSubmission.class));

            timedService.process(key, submission());

            ticker.advance(Duration.ofSeconds(29));
            timedService.process(key, submission());
            verify(mailService, times(1)).send(any(FormSubmission.class));

            ticker.advance(Duration.ofSeconds(2));
            timedService.process(key, submission());
            verify(mailService, times(2)).send(any(FormSubmission.class));
        }
    }

    @Nested
    @DisplayName("Concurrency tests")
    class Concurrency {

        @Test
        void concurrentRequestsWithTheSameKeySendExactlyOnce() throws Exception {
            int threads = 20;
            UUID key = UUID.randomUUID();

            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            Queue<Object> outcomes = new ConcurrentLinkedQueue<>();

            // The winner blocks inside the send until the test lets it go.
            doAnswer(inv -> {
                release.await(10, TimeUnit.SECONDS);
                return null;
            }).when(mailService).send(any(FormSubmission.class));

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        service.process(key, submission());
                        outcomes.add("OK");
                    } catch (ApiException e) {
                        outcomes.add(e.getStatus());
                    } catch (Throwable t) {
                        outcomes.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            await().atMost(5, TimeUnit.SECONDS).until(() -> done.getCount() == 1);
            release.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(outcomes).hasSize(threads);
            assertThat(outcomes.stream().filter("OK"::equals).count()).isEqualTo(1);
            assertThat(outcomes.stream().filter(HttpStatus.CONFLICT::equals).count()).isEqualTo(threads - 1);

            IdempotencyRecord record = cache.getIfPresent(key);
            assertThat(record).isNotNull();
            assertThat(record.status()).isEqualTo(IdempotencyRecord.Status.SUCCEEDED);
            verify(mailService, times(1)).send(any(FormSubmission.class));
        }

        @Test
        void concurrentRequestsWithDifferentKeysAllSend() throws Exception {
            int threads = 20;

            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            Queue<Object> outcomes = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < threads; i++) {
                UUID key = UUID.randomUUID();
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        service.process(key, submission());
                        outcomes.add("OK");
                    } catch (Throwable t) {
                        outcomes.add(t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(outcomes).hasSize(threads).containsOnly("OK");
            verify(mailService, times(threads)).send(any(FormSubmission.class));
        }
    }
}