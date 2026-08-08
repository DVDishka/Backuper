package ru.dvdishka.backuper.backend.storage;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import java.net.ProtocolException;
import java.net.http.HttpHeaders;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import ru.dvdishka.backuper.backend.storage.util.Retriable;

final class WebDavRetryPolicy {

    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(60);
    static final int MAX_TIMEOUT_ATTEMPTS = 2;

    private WebDavRetryPolicy() {
    }

    static boolean isTransientStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429
                || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    static boolean isPermanentTransportFailure(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ProtocolException
                    || cause instanceof SSLHandshakeException
                    || cause instanceof SSLPeerUnverifiedException
                    || cause instanceof SSLProtocolException
                    || cause instanceof CertificateException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    static Retriable.RetryDecision transportRetryDecision(Throwable failure,
                                                           Retriable.RetryContext context) {
        if (isTimeoutFailure(failure) && context.failedAttempt() >= MAX_TIMEOUT_ATTEMPTS) {
            return Retriable.RetryDecision.stop();
        }
        return Retriable.RetryDecision.retry(context.defaultDelay());
    }

    private static boolean isTimeoutFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof HttpTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    static Retriable.RetryDecision retryDecision(HttpHeaders headers, Duration defaultRetryDelay) {
        return headers.firstValue("Retry-After")
                .map(value -> retryDecision(value, defaultRetryDelay, Clock.systemUTC()))
                .orElseGet(() -> Retriable.RetryDecision.retry(defaultRetryDelay));
    }

    static Retriable.RetryDecision retryDecision(String retryAfter, Duration defaultRetryDelay, Clock clock) {
        String value = retryAfter.strip();
        Retriable.RetryDecision delaySecondsDecision = delaySecondsDecision(value);
        if (delaySecondsDecision != null) {
            return delaySecondsDecision;
        }

        try {
            Duration delay = Duration.between(clock.instant(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            if (delay.isNegative()) {
                delay = Duration.ZERO;
            }
            return delay.compareTo(MAX_RETRY_AFTER) > 0
                    ? Retriable.RetryDecision.stop()
                    : Retriable.RetryDecision.retry(delay);
        } catch (DateTimeParseException ignored) {
            return Retriable.RetryDecision.retry(defaultRetryDelay);
        }
    }

    private static Retriable.RetryDecision delaySecondsDecision(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return null;
            }
        }

        long maxSeconds = MAX_RETRY_AFTER.toSeconds();
        long seconds = 0;
        for (int index = 0; index < value.length(); index++) {
            seconds = seconds * 10 + value.charAt(index) - '0';
            if (seconds > maxSeconds) {
                return Retriable.RetryDecision.stop();
            }
        }
        return Retriable.RetryDecision.retry(Duration.ofSeconds(seconds));
    }
}
