package ru.dvdishka.backuper.storage;

import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.backend.storage.util.Retriable;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RetriableTest {

    private final Retriable.RetriableExceptionHandler exceptionHandler = new Retriable.RetriableExceptionHandler() {

        @Override
        public void handleRegularException(Exception e) {
        }

        @Override
        public RuntimeException handleFinalException(Exception e) {
            return new IllegalStateException("Retry failed", e);
        }
    };

    @Test
    public void preservesInterruptionAndStopsBeforeAnotherAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        try {
            Thread.currentThread().interrupt();
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> ((Retriable<Void>) () -> {
                        attempts.incrementAndGet();
                        throw new IOException("Transient failure");
                    }).retry(exceptionHandler, 5, 3_000));

            assertTrue(exception.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, attempts.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void stopsAfterTheConfiguredNumberOfAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class,
                () -> ((Retriable<Void>) () -> {
                    attempts.incrementAndGet();
                    throw new IOException("Transient failure");
                }).retry(exceptionHandler, 3, 0));

        assertEquals(3, attempts.get());
    }

    @Test
    public void letsTheExceptionHandlerOverrideTheRetryDelay() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger waits = new AtomicInteger();
        Retriable.RetriableExceptionHandler immediateRetryHandler = new Retriable.RetriableExceptionHandler() {

            @Override
            public Retriable.RetryDecision decide(Exception e, Retriable.RetryContext context) {
                return Retriable.RetryDecision.retry(Duration.ofSeconds(30));
            }

            @Override
            public void handleRegularException(Exception e) {
            }

            @Override
            public RuntimeException handleFinalException(Exception e) {
                return new IllegalStateException("Retry failed", e);
            }
        };

        int result = ((Retriable<Integer>) () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("Transient failure");
            }
            return 42;
        }).retry(immediateRetryHandler, 2, 3_000, delay -> {
            assertEquals(Duration.ofSeconds(30), delay);
            waits.incrementAndGet();
        });

        assertEquals(42, result);
        assertEquals(2, attempts.get());
        assertEquals(1, waits.get());
    }

    @Test
    public void preservesEarlierFailuresAsSuppressedExceptions() {
        AtomicInteger attempts = new AtomicInteger();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ((Retriable<Void>) () -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IOException("First failure");
                    }
                    throw new IllegalArgumentException("Final failure");
                }).retry(exceptionHandler, 2, 0));

        assertEquals(1, exception.getSuppressed().length);
        assertEquals("First failure", exception.getSuppressed()[0].getMessage());
    }

    @Test
    public void stopsImmediatelyWhenTheOperationThrowsInterruptedException() {
        AtomicInteger attempts = new AtomicInteger();

        try {
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> ((Retriable<Void>) () -> {
                        attempts.incrementAndGet();
                        throw new InterruptedException("Operation interrupted");
                    }).retry(exceptionHandler, 5, 3_000,
                            delay -> { throw new AssertionError("Retry waiter must not run"); }));

            assertTrue(exception.getCause() instanceof InterruptedException);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, attempts.get());
        } finally {
            Thread.interrupted();
        }
    }
}
