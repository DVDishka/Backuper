package ru.dvdishka.backuper.backend.storage;

import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.backend.storage.util.Retriable;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProtocolException;
import java.net.http.HttpTimeoutException;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebDavRetryPolicyTest {

    @Test
    public void classifiesPermanentTransportFailuresThroughTheirCauseChain() {
        assertTrue(WebDavRetryPolicy.isPermanentTransportFailure(new ProtocolException("Invalid response")));
        assertTrue(WebDavRetryPolicy.isPermanentTransportFailure(new SSLHandshakeException("Invalid certificate")));
        assertTrue(WebDavRetryPolicy.isPermanentTransportFailure(new SSLPeerUnverifiedException("Unverified peer")));
        assertTrue(WebDavRetryPolicy.isPermanentTransportFailure(new SSLProtocolException("Invalid TLS record")));
        assertTrue(WebDavRetryPolicy.isPermanentTransportFailure(new CertificateException("Invalid certificate")));
        assertTrue(WebDavRetryPolicy.isPermanentTransportFailure(
                new IOException("Wrapped TLS failure", new SSLHandshakeException("Invalid certificate"))));
    }

    @Test
    public void keepsOrdinaryConnectivityFailuresRetriable() {
        assertFalse(WebDavRetryPolicy.isPermanentTransportFailure(new ConnectException("Connection refused")));
        assertFalse(WebDavRetryPolicy.isPermanentTransportFailure(new HttpTimeoutException("Timed out")));
        assertFalse(WebDavRetryPolicy.isPermanentTransportFailure(new IOException("Connection reset")));
    }

    @Test
    public void classifiesOnlySupportedTransientHttpStatuses() {
        for (int statusCode : new int[]{408, 425, 429, 500, 502, 503, 504}) {
            assertTrue(WebDavRetryPolicy.isTransientStatus(statusCode),
                    () -> "Expected HTTP %d to be transient".formatted(statusCode));
        }
        for (int statusCode : new int[]{400, 401, 403, 404, 409, 412, 423, 501, 505, 507, 508}) {
            assertFalse(WebDavRetryPolicy.isTransientStatus(statusCode),
                    () -> "Expected HTTP %d to be permanent".formatted(statusCode));
        }
    }

    @Test
    public void limitsFullRequestTimeoutsToTwoAttempts() {
        Duration defaultDelay = Duration.ofSeconds(3);
        HttpTimeoutException timeout = new HttpTimeoutException("Timed out");

        assertTrue(WebDavRetryPolicy.transportRetryDecision(timeout,
                new Retriable.RetryContext(1, 5, defaultDelay)).retry());
        assertFalse(WebDavRetryPolicy.transportRetryDecision(timeout,
                new Retriable.RetryContext(2, 5, defaultDelay)).retry());
        assertTrue(WebDavRetryPolicy.transportRetryDecision(new IOException("Connection reset"),
                new Retriable.RetryContext(4, 5, defaultDelay)).retry());
    }

    @Test
    public void parsesAndBoundsRetryAfterValues() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-08T12:00:00Z"), ZoneOffset.UTC);
        String retryAt = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(clock.instant().plusSeconds(5), ZoneOffset.UTC));
        Duration defaultDelay = Duration.ofSeconds(3);

        assertEquals(Duration.ZERO,
                WebDavRetryPolicy.retryDecision("0", defaultDelay, clock).delay());
        assertEquals(Duration.ofSeconds(5),
                WebDavRetryPolicy.retryDecision(retryAt, defaultDelay, clock).delay());
        assertEquals(Duration.ofSeconds(5),
                WebDavRetryPolicy.retryDecision(" 5 ", defaultDelay, clock).delay());
        assertTrue(WebDavRetryPolicy.retryDecision("60", defaultDelay, clock).retry());
        assertFalse(WebDavRetryPolicy.retryDecision("61", defaultDelay, clock).retry());
        assertFalse(WebDavRetryPolicy.retryDecision("120", defaultDelay, clock).retry());
        assertFalse(WebDavRetryPolicy.retryDecision(
                "999999999999999999999999999999", defaultDelay, clock).retry());
        assertEquals(defaultDelay,
                WebDavRetryPolicy.retryDecision("invalid", defaultDelay, clock).delay());
        assertEquals(defaultDelay,
                WebDavRetryPolicy.retryDecision("-1", defaultDelay, clock).delay());
        assertEquals(defaultDelay,
                WebDavRetryPolicy.retryDecision("+5", defaultDelay, clock).delay());
    }
}
