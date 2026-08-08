package ru.dvdishka.backuper.backend.storage.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.exception.StorageQuotaExceededException;

@FunctionalInterface
public interface Retriable<T> {

    int DEFAULT_RETRIES = 5;
    int DEFAULT_RETRY_DELAY_MILLIS = 3000;

    /***
     * Define the main logic to be retried here
     */
    T run() throws Exception;

    /***
     * Uses the supplied handler to decide whether another attempt should run. Storage exceptions are
     * passed to {@link RetriableExceptionHandler#decide(Exception, RetryContext)} but are not converted
     * by {@link RetriableExceptionHandler#handleFinalException(Exception)}.
     * @throws StorageMethodException Something went wrong while executing some operation even after retries
     * @throws StorageConnectionException Failed to connect to storage even after retries
     * @throws StorageLimitException Storage limit exceeded
     * @throws StorageQuotaExceededException Storage quota exceeded even after retries
     */
    default T retry(RetriableExceptionHandler exceptionHandler, int maxAttempts, int retryDelayMillis) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
        return retry(exceptionHandler, maxAttempts, retryDelayMillis, Thread::sleep);
    }

    default T retry(RetriableExceptionHandler exceptionHandler, int maxAttempts, int retryDelayMillis,
                    RetryWaiter retryWaiter) throws StorageMethodException, StorageConnectionException,
            StorageLimitException, StorageQuotaExceededException {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        if (retryDelayMillis < 0) {
            throw new IllegalArgumentException("retryDelayMillis must not be negative");
        }

        Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        Objects.requireNonNull(retryWaiter, "retryWaiter");
        Duration defaultRetryDelay = Duration.ofMillis(retryDelayMillis);
        List<Exception> failures = new ArrayList<>();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return run();
            } catch (Exception e) {
                failures.add(e);
                if (e instanceof InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw finalException(exceptionHandler, interruptedException, failures);
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw finalException(exceptionHandler,
                            new InterruptedException("Retry operation interrupted"), failures);
                }

                RetryContext context = new RetryContext(attempt, maxAttempts, defaultRetryDelay);
                RetryDecision decision;
                try {
                    decision = attempt == maxAttempts
                            ? RetryDecision.stop()
                            : Objects.requireNonNull(exceptionHandler.decide(e, context), "retry decision");
                } catch (RuntimeException handlerException) {
                    throw addPreviousFailures(handlerException, failures);
                }

                if (!decision.retry()) {
                    throw finalException(exceptionHandler, e, failures);
                }

                Backuper backuper = Backuper.getInstance();
                if (backuper != null && backuper.getLogManager() != null) {
                    backuper.getLogManager().devWarn("Operation failed, retrying in "
                            + decision.delay().toMillis() + " ms... (" + attempt + "/" + maxAttempts + ")");
                    backuper.getLogManager().devWarn(e);
                }
                if (!(e instanceof StorageLimitException || e instanceof StorageQuotaExceededException
                        || e instanceof StorageConnectionException || e instanceof StorageMethodException)) {
                    try {
                        exceptionHandler.handleRegularException(e);
                    } catch (RuntimeException handlerException) {
                        throw addPreviousFailures(handlerException, failures);
                    }
                }

                try {
                    retryWaiter.await(decision.delay());
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw finalException(exceptionHandler, interruptedException, failures);
                } catch (RuntimeException waitException) {
                    throw addPreviousFailures(waitException, failures);
                }
            }
        }

        // Unreachable code
        throw new RuntimeException("Unexpected error in Retriable logic");
    }

    private static RuntimeException finalException(RetriableExceptionHandler exceptionHandler, Exception exception,
                                                   List<Exception> failures) {
        RuntimeException finalException;
        try {
            finalException = switch (exception) {
                case StorageConnectionException storageConnectionException -> storageConnectionException;
                case StorageLimitException storageLimitException -> storageLimitException;
                case StorageQuotaExceededException storageQuotaExceededException -> storageQuotaExceededException;
                case StorageMethodException storageMethodException -> storageMethodException;
                default -> Objects.requireNonNull(exceptionHandler.handleFinalException(exception),
                        "final exception");
            };
        } catch (RuntimeException handlerException) {
            return addPreviousFailures(handlerException, failures);
        }
        return addPreviousFailures(finalException, failures);
    }

    private static <E extends RuntimeException> E addPreviousFailures(E finalException,
                                                                       List<Exception> failures) {
        for (Exception failure : failures) {
            if (failure != finalException && failure != finalException.getCause()) {
                finalException.addSuppressed(failure);
            }
        }
        return finalException;
    }

    /***
     * Uses the supplied handler to decide whether another attempt should run. Storage exceptions are
     * rethrown unchanged when retrying stops.
     * @throws StorageMethodException Something went wrong while executing some operation even after retries
     * @throws StorageConnectionException Failed to connect to storage even after retries
     * @throws StorageLimitException Storage limit exceeded
     * @throws StorageQuotaExceededException Storage quota exceeded even after retries
     */
    default T retry(RetriableExceptionHandler exceptionHandler) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
        return retry(exceptionHandler, DEFAULT_RETRIES, DEFAULT_RETRY_DELAY_MILLIS);
    }

    interface RetriableExceptionHandler {

        /***
         * Decide whether and when another attempt should run.
         */
        default RetryDecision decide(Exception e, RetryContext context) {
            return RetryDecision.retry(context.defaultDelay());
        }

        /***
         * Handle exceptions but don't throw anything. It isn't the final exception, there will be some retries
         */
        void handleRegularException(Exception e);

        /***
         * Handle exceptions and return one that'll be thrown. It is the final exception, there will be no more retries
         */
        RuntimeException handleFinalException(Exception e);
    }

    record RetryContext(int failedAttempt, int maxAttempts, Duration defaultDelay) {

        public RetryContext {
            if (failedAttempt <= 0 || maxAttempts <= 0 || failedAttempt > maxAttempts) {
                throw new IllegalArgumentException("Invalid retry attempt context");
            }
            Objects.requireNonNull(defaultDelay, "defaultDelay");
            if (defaultDelay.isNegative()) {
                throw new IllegalArgumentException("defaultDelay must not be negative");
            }
        }
    }

    record RetryDecision(boolean retry, Duration delay) {

        public RetryDecision {
            Objects.requireNonNull(delay, "delay");
            if (delay.isNegative()) {
                throw new IllegalArgumentException("Retry delay must not be negative");
            }
        }

        public static RetryDecision retry(Duration delay) {
            return new RetryDecision(true, delay);
        }

        public static RetryDecision stop() {
            return new RetryDecision(false, Duration.ZERO);
        }
    }

    @FunctionalInterface
    interface RetryWaiter {

        void await(Duration delay) throws InterruptedException;
    }
}
