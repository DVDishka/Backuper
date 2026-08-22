package ru.dvdishka.backuper.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.backend.config.WebDavConfig;
import ru.dvdishka.backuper.backend.storage.WebDavStorage;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WebDavStorageTest {

    private InMemoryWebDavServer server;
    private WebDavStorage storage;

    @BeforeEach
    public void setUp() throws IOException {
        server = new InMemoryWebDavServer("test-user", "test-password");
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (storage != null) {
            storage.destroy();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void listsResourcesAndCalculatesDirectorySize() {
        server.addDirectory("/dav/backups/nested");
        server.addFile("/dav/backups/file name.txt", new byte[]{1, 2, 3, 4});
        server.addFile("/dav/backups/nested/child#.bin", new byte[]{5, 6, 7});
        server.additionalPropFindHrefs.add("/dav/backups/file%20name.txt");
        server.additionalPropFindHrefs.add("/dav/backups/nested/ignored-grandchild.bin");
        storage = createStorage(true, false);

        assertEquals(List.of("file name.txt", "nested"), storage.ls("backups"));
        assertFalse(storage.isFile("backups"));
        assertTrue(storage.isFile("backups/file name.txt"));
        assertEquals(7, storage.getDirByteSize("backups"));

        assertTrue(server.redirectCount.get() > 0);
        assertTrue(server.rawPaths.contains("/dav/backups/file%20name.txt"));
        server.assertHealthy();
    }

    @Test
    public void transfersRenamesAndAsynchronouslyDeletesFiles() throws IOException {
        storage = createStorage(true, false);
        byte[] payload = "streamed webdav payload".getBytes(StandardCharsets.UTF_8);
        BasicStorageProgressListener uploadProgress = new BasicStorageProgressListener();

        server.mutationRedirectMethod = "MKCOL";
        server.mutationRedirectLocation = "/dav/backups/%6eew%20dir/";
        storage.createDir("new dir", "backups");
        storage.uploadFile(new ByteArrayInputStream(payload), "a file #1.bin", "backups/new dir", uploadProgress);

        assertEquals(payload.length, uploadProgress.getCurrentProgress());
        assertArrayEquals(payload, server.files.get("/dav/backups/new dir/a file #1.bin"));
        assertNull(server.lastPutContentLength);
        assertEquals("chunked", server.lastPutTransferEncoding);

        BasicStorageProgressListener downloadProgress = new BasicStorageProgressListener();
        try (InputStream input = storage.downloadFile("backups/new dir/a file #1.bin", downloadProgress)) {
            assertArrayEquals(payload, input.readAllBytes());
        }
        assertEquals(payload.length, downloadProgress.getCurrentProgress());

        storage.renameFile("backups/new dir/a file #1.bin", "renamed & final.bin");
        assertTrue(storage.exists("backups/new dir/renamed & final.bin"));
        assertFalse(storage.exists("backups/new dir/a file #1.bin"));

        server.asyncDelete = true;
        storage.delete("backups/new dir/renamed & final.bin");
        assertFalse(storage.exists("backups/new dir/renamed & final.bin"));
        assertTrue(server.rawPaths.contains("/dav/backups/new%20dir/a%20file%20%231.bin"));
        assertTrue(server.rawPaths.contains("/dav/backups/new%20dir/renamed%20%26%20final.bin"));
        assertEquals(1, server.mutationRedirectCount.get());
        server.assertHealthy();
    }

    @Test
    public void bufferedUploadSendsFixedContentLength() {
        storage = createStorage(true, true);
        byte[] payload = new byte[128 * 1024];
        Arrays.fill(payload, (byte) 42);
        BasicStorageProgressListener progress = new BasicStorageProgressListener();

        server.mutationRedirectMethod = "PUT";
        server.mutationRedirectLocation = "/dav/backups/%66ixed-length.bin";
        storage.uploadFile(new ByteArrayInputStream(payload), "fixed-length.bin", "backups", progress);

        assertEquals(String.valueOf(payload.length), server.lastPutContentLength);
        assertNull(server.lastPutTransferEncoding);
        assertEquals(payload.length, progress.getCurrentProgress());
        assertArrayEquals(payload, server.files.get("/dav/backups/fixed-length.bin"));
        assertEquals(1, server.mutationRedirectCount.get());
        server.assertHealthy();
    }

    @Test
    public void streamingUploadDoesNotCloseSourceStream() {
        storage = createStorage(true, false);
        byte[] payload = "caller-owned stream".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean sourceClosed = new AtomicBoolean();
        InputStream sourceStream = new ByteArrayInputStream(payload) {
            @Override
            public void close() {
                sourceClosed.set(true);
            }
        };

        storage.uploadFile(sourceStream, "caller-owned.bin", "backups",
                new BasicStorageProgressListener());

        assertFalse(sourceClosed.get());
        assertArrayEquals(payload, server.files.get("/dav/backups/caller-owned.bin"));
        server.assertHealthy();
    }

    @Test
    public void retriesTransientMetadataFailuresButNotPermanentHttpFailures() {
        storage = createStorage(true, false);
        server.failNext("PROPFIND", 503, false);

        assertTrue(storage.exists("backups"));
        assertTrue(server.methodRequestCount("PROPFIND") >= 2);

        int requestsBeforePermanentFailure = server.methodRequestCount("PROPFIND");
        server.failAlways("PROPFIND", 401);
        assertThrows(StorageConnectionException.class, () -> storage.exists("backups"));
        assertEquals(requestsBeforePermanentFailure + 1, server.methodRequestCount("PROPFIND"));
        server.assertHealthy();
    }

    @Test
    public void retriesAConnectionFailureWhileParsingPropFindResponse() {
        String path = "/dav/backups/interrupted-metadata.bin";
        server.addFile(path, new byte[]{1, 2, 3});
        server.truncateNextPropFindResponse.set(true);
        storage = createStorage(true, false);

        assertTrue(storage.exists("backups/interrupted-metadata.bin"));

        assertEquals(2, server.methodRequestCount("PROPFIND"));
        server.assertHealthy();
    }

    @Test
    public void retriesBufferedUploadsWithAReplayableBody() {
        storage = createStorage(true, true);
        byte[] payload = "replayed buffered upload".getBytes(StandardCharsets.UTF_8);
        BasicStorageProgressListener progress = new BasicStorageProgressListener();
        server.failNext("PUT", 503, false);

        storage.uploadFile(new ByteArrayInputStream(payload), "replayed.bin", "backups", progress);

        assertArrayEquals(payload, server.files.get("/dav/backups/replayed.bin"));
        assertEquals(payload.length, progress.getCurrentProgress());
        assertEquals(2, server.methodRequestCount("PUT"));
        server.assertHealthy();
    }

    @Test
    public void reconcilesABufferedUploadCommittedBeforeATransientResponse() {
        storage = createStorage(true, true);
        byte[] payload = "committed buffered upload".getBytes(StandardCharsets.UTF_8);
        BasicStorageProgressListener progress = new BasicStorageProgressListener();
        server.failNext("PUT", 503, true);

        storage.uploadFile(new ByteArrayInputStream(payload), "committed.bin", "backups", progress);

        assertArrayEquals(payload, server.files.get("/dav/backups/committed.bin"));
        assertEquals(payload.length, progress.getCurrentProgress());
        assertEquals(1, server.methodRequestCount("PUT"));
        server.assertHealthy();
    }

    @Test
    public void doesNotMistakeAPreexistingSameLengthFileForACommittedUpload() {
        storage = createStorage(true, true);
        String path = "/dav/backups/existing.bin";
        byte[] payload = new byte[]{4, 5, 6};
        server.addFile(path, new byte[]{1, 2, 3});
        server.failNext("PUT", 503, false);

        storage.uploadFile(new ByteArrayInputStream(payload), "existing.bin", "backups",
                new BasicStorageProgressListener());

        assertArrayEquals(payload, server.files.get(path));
        assertEquals(2, server.methodRequestCount("PUT"));
        server.assertHealthy();
    }

    @Test
    public void doesNotConfirmAnotherWritersSameLengthFileAsACommittedUpload()
            throws InterruptedException {
        storage = createStorage(true, true);
        String path = "/dav/backups/concurrent.bin";
        byte[] payload = new byte[]{4, 5, 6};
        byte[] concurrentContent = new byte[]{1, 2, 3};
        server.failNext("PUT", 503, false, "1");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread uploadThread = new Thread(() -> {
            try {
                storage.uploadFile(new ByteArrayInputStream(payload), "concurrent.bin", "backups",
                        new BasicStorageProgressListener());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        uploadThread.start();

        assertTrue(server.awaitTransientFailure(1, TimeUnit.SECONDS));
        server.addFile(path, concurrentContent);
        uploadThread.join(5_000);

        assertFalse(uploadThread.isAlive());
        assertTrue(failure.get() instanceof StorageMethodException);
        assertArrayEquals(concurrentContent, server.files.get(path));
        assertEquals(2, server.methodRequestCount("PUT"));
        server.assertHealthy();
    }

    @Test
    public void doesNotRetryAStreamingUploadWithAConsumedBody() {
        storage = createStorage(true, false);
        server.failNext("PUT", 503, false);

        assertThrows(StorageConnectionException.class,
                () -> storage.uploadFile(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                        "streaming-retry.bin", "backups", new BasicStorageProgressListener()));

        assertEquals(1, server.methodRequestCount("PUT"));
        assertFalse(server.files.containsKey("/dav/backups/streaming-retry.bin"));
        server.assertHealthy();
    }

    @Test
    public void doesNotReplayAStreamingUploadCommittedBeforeATransientResponse() {
        storage = createStorage(true, false);
        byte[] payload = new byte[]{1, 2, 3};
        server.failNext("PUT", 503, true);

        assertThrows(StorageConnectionException.class,
                () -> storage.uploadFile(new ByteArrayInputStream(payload),
                        "committed-stream.bin", "backups", new BasicStorageProgressListener()));

        assertArrayEquals(payload, server.files.get("/dav/backups/committed-stream.bin"));
        assertEquals(1, server.methodRequestCount("PUT"));
        server.assertHealthy();
    }

    @Test
    public void reconcilesMutationsCommittedBeforeATransientFailureResponse() {
        storage = createStorage(true, false);

        server.failNext("MKCOL", 503, true);
        storage.createDir("recovered", "backups");
        assertTrue(storage.exists("backups/recovered"));
        assertEquals(1, server.methodRequestCount("MKCOL"));

        server.addFile("/dav/backups/recovered/source.bin", new byte[]{1, 2, 3});
        server.failNext("MOVE", 503, true);
        storage.renameFile("backups/recovered/source.bin", "target.bin");
        assertFalse(storage.exists("backups/recovered/source.bin"));
        assertTrue(storage.exists("backups/recovered/target.bin"));
        assertEquals(1, server.methodRequestCount("MOVE"));

        server.failNext("DELETE", 503, true);
        storage.delete("backups/recovered/target.bin");
        assertFalse(storage.exists("backups/recovered/target.bin"));
        assertEquals(1, server.methodRequestCount("DELETE"));
        server.assertHealthy();
    }

    @Test
    public void retriesUncommittedMutations() {
        storage = createStorage(true, false);

        server.failNext("MKCOL", 503, false);
        storage.createDir("retried", "backups");
        assertTrue(storage.exists("backups/retried"));
        assertEquals(2, server.methodRequestCount("MKCOL"));

        server.addFile("/dav/backups/retried/source.bin", new byte[]{1, 2, 3});
        server.failNext("MOVE", 503, false);
        storage.renameFile("backups/retried/source.bin", "target.bin");
        assertFalse(storage.exists("backups/retried/source.bin"));
        assertTrue(storage.exists("backups/retried/target.bin"));
        assertEquals(2, server.methodRequestCount("MOVE"));

        server.failNext("DELETE", 503, false);
        storage.delete("backups/retried/target.bin");
        assertFalse(storage.exists("backups/retried/target.bin"));
        assertEquals(2, server.methodRequestCount("DELETE"));
        server.assertHealthy();
    }

    @Test
    public void reconcilesMutationsCommittedOnTheFinalAttempt() {
        storage = createStorage(true, false);

        server.failBeforeThenAfterMutation("MKCOL", 503, 4, "0");
        storage.createDir("terminal", "backups");
        assertTrue(storage.exists("backups/terminal"));
        assertEquals(5, server.methodRequestCount("MKCOL"));

        server.addFile("/dav/backups/terminal/source.bin", new byte[]{1, 2, 3});
        server.failBeforeThenAfterMutation("MOVE", 503, 4, "0");
        storage.renameFile("backups/terminal/source.bin", "target.bin");
        assertFalse(storage.exists("backups/terminal/source.bin"));
        assertTrue(storage.exists("backups/terminal/target.bin"));
        assertEquals(5, server.methodRequestCount("MOVE"));

        server.failBeforeThenAfterMutation("DELETE", 503, 4, "0");
        storage.delete("backups/terminal/target.bin");
        assertFalse(storage.exists("backups/terminal/target.bin"));
        assertEquals(5, server.methodRequestCount("DELETE"));
        server.assertHealthy();
    }

    @Test
    public void reconcilesABufferedUploadCommittedOnTheFinalAttempt() {
        storage = createStorage(true, true);
        byte[] payload = "terminal buffered upload".getBytes(StandardCharsets.UTF_8);
        BasicStorageProgressListener progress = new BasicStorageProgressListener();
        server.failBeforeThenAfterMutation("PUT", 503, 4, "0");

        storage.uploadFile(new ByteArrayInputStream(payload), "terminal.bin", "backups", progress);

        assertArrayEquals(payload, server.files.get("/dav/backups/terminal.bin"));
        assertEquals(payload.length, progress.getCurrentProgress());
        assertEquals(5, server.methodRequestCount("PUT"));
        server.assertHealthy();
    }

    @Test
    public void reconcilesACommittedMutationWhenRetryAfterStopsRetries() {
        storage = createStorage(true, false);
        server.failNext("MKCOL", 503, true, "61");

        storage.createDir("retry-after-stop", "backups");

        assertTrue(storage.exists("backups/retry-after-stop"));
        assertEquals(1, server.methodRequestCount("MKCOL"));
        server.assertHealthy();
    }

    @Test
    public void refusesToReplayDeleteWhenTheResourceWasReplaced() throws InterruptedException {
        storage = createStorage(true, false);
        String path = "/dav/backups/replaced.bin";
        byte[] replacement = new byte[]{9, 8, 7};
        server.addFile(path, new byte[]{1, 2, 3});
        server.failNext("DELETE", 503, false, "1");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread deleteThread = new Thread(() -> {
            try {
                storage.delete("backups/replaced.bin");
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        deleteThread.start();

        assertTrue(server.awaitTransientFailure(1, TimeUnit.SECONDS));
        server.addFile(path, replacement);
        deleteThread.join(5_000);

        assertFalse(deleteThread.isAlive());
        assertTrue(failure.get() instanceof StorageMethodException);
        assertTrue(failure.get().getMessage().contains("resource changed"));
        assertArrayEquals(replacement, server.files.get(path));
        assertEquals(1, server.methodRequestCount("DELETE"));
        server.assertHealthy();
    }

    @Test
    public void refusesToReplayRenameWhenTheSourceWasReplaced() throws InterruptedException {
        storage = createStorage(true, false);
        String sourcePath = "/dav/backups/rename-source.bin";
        String targetPath = "/dav/backups/rename-target.bin";
        byte[] replacement = new byte[]{9, 8, 7};
        server.addFile(sourcePath, new byte[]{1, 2, 3});
        server.failNext("MOVE", 503, false, "1");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread renameThread = new Thread(() -> {
            try {
                storage.renameFile("backups/rename-source.bin", "rename-target.bin");
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        renameThread.start();

        assertTrue(server.awaitTransientFailure(1, TimeUnit.SECONDS));
        server.addFile(sourcePath, replacement);
        renameThread.join(5_000);

        assertFalse(renameThread.isAlive());
        assertTrue(failure.get() instanceof StorageMethodException);
        assertTrue(failure.get().getMessage().contains("resource changed"));
        assertArrayEquals(replacement, server.files.get(sourcePath));
        assertFalse(server.files.containsKey(targetPath));
        assertEquals(1, server.methodRequestCount("MOVE"));
        server.assertHealthy();
    }

    @Test
    public void refusesToConfirmRenameWhenTheTargetDoesNotMatch() throws InterruptedException {
        storage = createStorage(true, false);
        String sourcePath = "/dav/backups/mismatch-source.bin";
        String targetPath = "/dav/backups/mismatch-target.bin";
        server.addFile(sourcePath, new byte[]{1, 2, 3});
        server.failNext("MOVE", 503, false, "1");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread renameThread = new Thread(() -> {
            try {
                storage.renameFile("backups/mismatch-source.bin", "mismatch-target.bin");
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        renameThread.start();

        assertTrue(server.awaitTransientFailure(1, TimeUnit.SECONDS));
        server.remove(sourcePath);
        server.addFile(targetPath, new byte[]{9, 8, 7});
        renameThread.join(5_000);

        assertFalse(renameThread.isAlive());
        assertTrue(failure.get() instanceof StorageMethodException);
        assertTrue(failure.get().getMessage().contains("could not prove"));
        assertArrayEquals(new byte[]{9, 8, 7}, server.files.get(targetPath));
        assertEquals(1, server.methodRequestCount("MOVE"));
        server.assertHealthy();
    }

    @Test
    public void destroyStopsAnOperationInRetryBackoff() throws InterruptedException {
        storage = createStorage(true, false);
        server.failNext("PROPFIND", 503, false, "60");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread requestThread = new Thread(() -> {
            try {
                storage.exists("backups");
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        requestThread.start();

        assertTrue(server.awaitTransientFailure(1, TimeUnit.SECONDS));
        storage.destroy();
        requestThread.join(1_000);

        assertFalse(requestThread.isAlive());
        assertTrue(failure.get() instanceof StorageConnectionException);
        assertEquals(1, server.methodRequestCount("PROPFIND"));
        server.assertHealthy();
    }

    @Test
    public void refusesToReplayDeleteWithoutAStrongEtag() {
        storage = createStorage(true, false);
        String path = "/dav/backups/no-etag.bin";
        byte[] content = new byte[]{1, 2, 3};
        server.addFile(path, content);
        server.etags.remove(path);
        server.failNext("DELETE", 503, false);

        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.delete("backups/no-etag.bin"));

        assertTrue(exception.getMessage().contains("did not provide a strong ETag"));
        assertArrayEquals(content, server.files.get(path));
        assertEquals(1, server.methodRequestCount("DELETE"));
        server.assertHealthy();
    }

    @Test
    public void refusesToReplayAStreamingUploadAfterRedirect() {
        storage = createStorage(true, false);
        server.mutationRedirectMethod = "PUT";
        server.mutationRedirectLocation = "/dav/backups/%73treaming.bin";

        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.uploadFile(new ByteArrayInputStream(new byte[]{1, 2, 3}),
                        "streaming.bin", "backups", new BasicStorageProgressListener()));

        assertTrue(exception.getMessage().contains("bufferUploadsToDisk"));
        assertFalse(server.files.containsKey("/dav/backups/streaming.bin"));
        server.assertHealthy();
    }

    @Test
    public void refusesBasicAuthenticationOverHttpByDefault() {
        storage = createStorage(false, false);

        StorageConnectionException exception = assertThrows(StorageConnectionException.class,
                () -> storage.exists("backups"));

        assertTrue(exception.getMessage().contains("allowInsecureHttp"));
        assertEquals(0, server.requestCount.get());
    }

    @Test
    public void rejectsBasicAuthenticationUsernamesContainingAColon() {
        storage = createStorage(server.baseUrl(), true, false, 10, 5, "domain:test-user");

        StorageConnectionException exception = assertThrows(StorageConnectionException.class,
                () -> storage.exists("backups"));

        assertTrue(exception.getMessage().contains("username cannot contain ':'"));
        assertEquals(0, server.requestCount.get());
    }

    @Test
    public void refusesCrossOriginRedirects() {
        server.redirectLocation = "http://redirect-user:redirect-secret@example.invalid/dav/backups/?token=redirect-token";
        storage = createStorage(true, false);

        StorageConnectionException exception = assertThrows(StorageConnectionException.class,
                () -> storage.exists("backups"));

        assertTrue(exception.getMessage().contains("unsafe WebDAV redirect"));
        assertFalse(exception.getMessage().contains("redirect-secret"));
        assertFalse(exception.getMessage().contains("redirect-token"));
        server.assertHealthy();
    }

    @Test
    public void rejectsMalformedRedirectsWithoutExposingTheirLocation() {
        server.redirectLocation = "http://[invalid/redirect-secret";
        storage = createStorage(true, false);

        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.exists("backups"));

        assertTrue(exception.getMessage().contains("invalid redirect URI"));
        assertFalse(exception.getMessage().contains("redirect-secret"));
        server.assertHealthy();
    }

    @Test
    public void rejectsRedirectsWithRelativeSegmentsOrEncodedSeparators() {
        server.redirectLocation = "/dav/backups/../private/";
        storage = createStorage(true, false);

        assertThrows(StorageConnectionException.class, () -> storage.exists("backups"));

        server.redirectLocation = "/dav/backups%2Fprivate/";
        assertThrows(StorageConnectionException.class, () -> storage.exists("backups"));
        server.assertHealthy();
    }

    @Test
    public void defersInvalidUrlFailureUntilTheStorageIsUsed() {
        storage = createStorage("https://example.invalid/dav?token=config-secret", true, false, 10);

        StorageConnectionException exception = assertThrows(StorageConnectionException.class,
                () -> storage.exists("backups"));

        assertTrue(exception.getMessage().contains("Invalid WebDAV URL"));
        assertFalse(exception.getMessage().contains("config-secret"));
        assertEquals(0, server.requestCount.get());
    }

    @Test
    public void rejectsEndpointPathsWithRelativeSegmentsOrEncodedSeparators() {
        storage = createStorage("https://example.invalid/dav/../private/", true, false, 10);
        assertThrows(StorageConnectionException.class, () -> storage.exists("backups"));

        storage.destroy();
        storage = createStorage("https://example.invalid/dav%2Fprivate/", true, false, 10);
        assertThrows(StorageConnectionException.class, () -> storage.exists("backups"));
        assertEquals(0, server.requestCount.get());
    }

    @Test
    public void rejectsMismatchedPropFindMetadata() {
        server.addFile("/dav/backups/requested.bin", new byte[]{1});
        server.propFindHrefOverride = "/dav/backups/unrelated.bin";
        storage = createStorage(true, false);

        assertThrows(StorageMethodException.class, () -> storage.exists("backups/requested.bin"));

        server.propFindHrefOverride = "http://href-user:href-secret@example.invalid/dav/backups/requested.bin?token=href-token";
        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.exists("backups/requested.bin"));
        assertTrue(exception.getMessage().contains("unsafe resource href"));
        assertFalse(exception.getMessage().contains("href-secret"));
        assertFalse(exception.getMessage().contains("href-token"));

        server.propFindHrefOverride = "/dav/backups/%2E%2E/requested.bin";
        assertThrows(StorageMethodException.class, () -> storage.exists("backups/requested.bin"));

        server.propFindHrefOverride = "/dav/backups%2Frequested.bin";
        assertThrows(StorageMethodException.class, () -> storage.exists("backups/requested.bin"));
        server.assertHealthy();
    }

    @Test
    public void boundsAsynchronousDeleteConfirmationByItsConfiguredTimeout() {
        server.addFile("/dav/backups/slow-delete.bin", new byte[]{1});
        server.asyncDelete = true;
        server.deleteConfirmationDelayMillis = 2_000;
        storage = createStorage(server.baseUrl(), true, false, 10, 1);

        long startedAt = System.nanoTime();
        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.delete("backups/slow-delete.bin"));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(exception.getMessage().contains("not confirmed within 1 seconds"));
        assertTrue(elapsedMillis < 1_800, "Delete confirmation took %d ms".formatted(elapsedMillis));
    }

    @Test
    public void asynchronousDeleteConfirmationHonorsRetryAfterAndDeadline() {
        server.addFile("/dav/backups/rate-limited-delete.bin", new byte[]{1});
        server.asyncDelete = true;
        server.deleteConfirmationRetryAfter.set("10");
        storage = createStorage(server.baseUrl(), true, false, 10, 1);

        long startedAt = System.nanoTime();
        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.delete("backups/rate-limited-delete.bin"));
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertTrue(exception.getMessage().contains("not confirmed within 1 seconds"));
        assertTrue(elapsedMillis < 1_800, "Delete confirmation took %d ms".formatted(elapsedMillis));
        assertEquals(2, server.methodRequestCount("PROPFIND"));
        server.assertHealthy();
    }

    @Test
    public void rejectsUnknownFileSizesAndListingAFileAsADirectory() {
        server.addFile("/dav/backups/no-size.bin", new byte[]{1, 2, 3});
        server.omitContentLengthPath = "/dav/backups/no-size.bin";
        storage = createStorage(true, false);

        assertTrue(storage.exists("backups/no-size.bin"));
        StorageMethodException exception = assertThrows(StorageMethodException.class,
                () -> storage.getDirByteSize("backups/no-size.bin"));
        assertTrue(exception.getMessage().contains("did not return getcontentlength"));
        assertThrows(StorageMethodException.class, () -> storage.ls("backups/no-size.bin"));
        server.assertHealthy();
    }

    @Test
    public void respectsPropFindXmlEncodingDeclaration() {
        server.addFile("/dav/backups/caf\u00e9.txt", new byte[]{1});
        server.propFindHrefOverride = "/dav/backups/caf\u00e9.txt";
        server.propFindCharset = StandardCharsets.ISO_8859_1;
        storage = createStorage(true, false);

        assertTrue(storage.exists("backups/caf\u00e9.txt"));
        server.assertHealthy();
    }

    @Test
    public void timesOutWhileReadingAStalledDownloadBody() throws IOException {
        server.addFile("/dav/backups/stalled.bin", new byte[]{1});
        server.stallDownloads = true;
        storage = createStorage(server.baseUrl(), true, false, 1);

        try (InputStream input = storage.downloadFile("backups/stalled.bin", new BasicStorageProgressListener())) {
            IOException exception = assertThrows(IOException.class, input::read);
            assertTrue(exception.getMessage().contains("timed out"));
        }
        server.assertHealthy();
    }

    private WebDavStorage createStorage(boolean allowInsecureHttp, boolean bufferUploadsToDisk) {
        return createStorage(server.baseUrl(), allowInsecureHttp, bufferUploadsToDisk, 10);
    }

    private WebDavStorage createStorage(String url, boolean allowInsecureHttp, boolean bufferUploadsToDisk, int timeoutSeconds) {
        return createStorage(url, allowInsecureHttp, bufferUploadsToDisk, timeoutSeconds, 5);
    }

    private WebDavStorage createStorage(String url, boolean allowInsecureHttp, boolean bufferUploadsToDisk,
                                        int timeoutSeconds, int deleteConfirmationTimeoutSeconds) {
        return createStorage(url, allowInsecureHttp, bufferUploadsToDisk,
                timeoutSeconds, deleteConfirmationTimeoutSeconds, "test-user");
    }

    private WebDavStorage createStorage(String url, boolean allowInsecureHttp, boolean bufferUploadsToDisk,
                                        int timeoutSeconds, int deleteConfirmationTimeoutSeconds, String username) {
        WebDavConfig config = mock(WebDavConfig.class);
        when(config.getId()).thenReturn("webdav-test");
        when(config.getUrl()).thenReturn(url);
        when(config.getUsername()).thenReturn(username);
        when(config.getPassword()).thenReturn("test-password");
        when(config.isAllowInsecureHttp()).thenReturn(allowInsecureHttp);
        when(config.getRequestTimeoutSeconds()).thenReturn(timeoutSeconds);
        when(config.getDeleteConfirmationTimeoutSeconds()).thenReturn(deleteConfirmationTimeoutSeconds);
        when(config.isBufferUploadsToDisk()).thenReturn(bufferUploadsToDisk);
        when(config.isProtocolLogging()).thenReturn(false);
        when(config.getPathSeparatorSymbol()).thenReturn("/");
        when(config.getBackupsFolder()).thenReturn("backups");

        WebDavStorage webDavStorage = new WebDavStorage(config);
        webDavStorage.setId("webdav-test");
        return webDavStorage;
    }

    private static class InMemoryWebDavServer implements AutoCloseable {

        private final HttpServer server;
        private final String expectedAuthorization;
        private final Set<String> directories = ConcurrentHashMap.newKeySet();
        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        private final Map<String, String> etags = new ConcurrentHashMap<>();
        private final Map<String, Integer> pendingDeletes = new ConcurrentHashMap<>();
        private final AtomicBoolean authorizationFailed = new AtomicBoolean();
        private final AtomicReference<Throwable> handlerFailure = new AtomicReference<>();
        private final AtomicReference<String> deleteConfirmationRetryAfter = new AtomicReference<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger redirectCount = new AtomicInteger();
        private final AtomicInteger mutationRedirectCount = new AtomicInteger();
        private final List<String> rawPaths = new CopyOnWriteArrayList<>();
        private final List<String> additionalPropFindHrefs = new CopyOnWriteArrayList<>();
        private final Map<String, AtomicInteger> methodRequestCounts = new ConcurrentHashMap<>();
        private final AtomicInteger transientFailuresBeforeMutation = new AtomicInteger();
        private final AtomicBoolean transientFailureAfterMutationPending = new AtomicBoolean();
        private final AtomicBoolean truncateNextPropFindResponse = new AtomicBoolean();
        private final AtomicLong etagSequence = new AtomicLong();

        private volatile boolean asyncDelete;
        private volatile boolean stallDownloads;
        private volatile long deleteConfirmationDelayMillis;
        private volatile String redirectLocation;
        private volatile String mutationRedirectMethod;
        private volatile String mutationRedirectLocation;
        private volatile String propFindHrefOverride;
        private volatile Charset propFindCharset = StandardCharsets.UTF_8;
        private volatile String omitContentLengthPath;
        private volatile String lastPutContentLength;
        private volatile String lastPutTransferEncoding;
        private volatile String transientFailureMethod;
        private volatile int transientFailureStatus;
        private volatile String transientFailureRetryAfter;
        private volatile String permanentFailureMethod;
        private volatile int permanentFailureStatus;
        private volatile CountDownLatch transientFailureObserved = new CountDownLatch(0);

        private InMemoryWebDavServer(String username, String password) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                    "%s:%s".formatted(username, password).getBytes(StandardCharsets.UTF_8));
            addDirectory("/dav");
            addDirectory("/dav/backups");
            server.createContext("/dav", this::handle);
        }

        private void start() {
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:%d/dav/".formatted(server.getAddress().getPort());
        }

        private void addDirectory(String path) {
            String normalizedPath = normalize(path);
            directories.add(normalizedPath);
            etags.put(normalizedPath, nextEtag());
        }

        private void addFile(String path, byte[] content) {
            String normalizedPath = normalize(path);
            files.put(normalizedPath, content.clone());
            etags.put(normalizedPath, nextEtag());
        }

        private void failNext(String method, int statusCode, boolean afterMutation) {
            failNext(method, statusCode, afterMutation, "0");
        }

        private void failNext(String method, int statusCode, boolean afterMutation, String retryAfter) {
            configureTransientFailure(method, statusCode, retryAfter);
            if (afterMutation) {
                transientFailureAfterMutationPending.set(true);
            } else {
                transientFailuresBeforeMutation.set(1);
            }
        }

        private void failBeforeThenAfterMutation(String method, int statusCode,
                                                 int failuresBeforeMutation, String retryAfter) {
            configureTransientFailure(method, statusCode, retryAfter);
            transientFailuresBeforeMutation.set(failuresBeforeMutation);
            transientFailureAfterMutationPending.set(true);
        }

        private void configureTransientFailure(String method, int statusCode, String retryAfter) {
            transientFailureMethod = method;
            transientFailureStatus = statusCode;
            transientFailureRetryAfter = retryAfter;
            transientFailureObserved = new CountDownLatch(1);
            transientFailuresBeforeMutation.set(0);
            transientFailureAfterMutationPending.set(false);
        }

        private boolean awaitTransientFailure(long timeout, TimeUnit unit) throws InterruptedException {
            return transientFailureObserved.await(timeout, unit);
        }

        private void failAlways(String method, int statusCode) {
            permanentFailureMethod = method;
            permanentFailureStatus = statusCode;
        }

        private int methodRequestCount(String method) {
            AtomicInteger count = methodRequestCounts.get(method);
            return count == null ? 0 : count.get();
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            String method = exchange.getRequestMethod();
            methodRequestCounts.computeIfAbsent(method, ignored -> new AtomicInteger()).incrementAndGet();
            rawPaths.add(exchange.getRequestURI().getRawPath());
            try {
                if (!expectedAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    authorizationFailed.set(true);
                    send(exchange, 401, "Unauthorized");
                    return;
                }
                if (permanentFailureMethod != null && permanentFailureMethod.equals(method)) {
                    exchange.getRequestBody().readAllBytes();
                    send(exchange, permanentFailureStatus, "Permanent failure");
                    return;
                }
                Integer transientFailureBeforeMutation = takeTransientFailure(method, false);
                if (transientFailureBeforeMutation != null) {
                    exchange.getRequestBody().readAllBytes();
                    addTransientFailureHeaders(exchange);
                    send(exchange, transientFailureBeforeMutation, "Transient failure");
                    transientFailureObserved.countDown();
                    return;
                }
                if (redirectLocation != null && "PROPFIND".equals(method)) {
                    exchange.getResponseHeaders().set("Location", redirectLocation);
                    redirectCount.incrementAndGet();
                    send(exchange, 302, "");
                    return;
                }
                if (mutationRedirectMethod != null
                        && mutationRedirectMethod.equals(method)
                        && mutationRedirectCount.compareAndSet(0, 1)) {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("Location", mutationRedirectLocation);
                    send(exchange, 308, "");
                    return;
                }

                Integer transientFailureAfterMutationStatus = takeTransientFailure(method, true);
                if (transientFailureAfterMutationStatus != null) {
                    addTransientFailureHeaders(exchange);
                }
                switch (method) {
                    case "PROPFIND" -> propFind(exchange);
                    case "MKCOL" -> makeCollection(exchange, transientFailureAfterMutationStatus);
                    case "PUT" -> put(exchange, transientFailureAfterMutationStatus);
                    case "GET" -> get(exchange);
                    case "MOVE" -> move(exchange, transientFailureAfterMutationStatus);
                    case "DELETE" -> delete(exchange, transientFailureAfterMutationStatus);
                    default -> send(exchange, 405, "Method Not Allowed");
                }
            } catch (Throwable throwable) {
                handlerFailure.compareAndSet(null, throwable);
                try {
                    send(exchange, 500, throwable.toString());
                } catch (Exception ignored) {
                }
            }
        }

        private Integer takeTransientFailure(String method, boolean afterMutation) {
            if (!method.equals(transientFailureMethod)) {
                return null;
            }
            if (afterMutation) {
                return transientFailureAfterMutationPending.compareAndSet(true, false)
                        ? transientFailureStatus
                        : null;
            }
            while (true) {
                int remainingFailures = transientFailuresBeforeMutation.get();
                if (remainingFailures <= 0) {
                    return null;
                }
                if (transientFailuresBeforeMutation.compareAndSet(
                        remainingFailures, remainingFailures - 1)) {
                    return transientFailureStatus;
                }
            }
        }

        private void addTransientFailureHeaders(HttpExchange exchange) {
            if (transientFailureRetryAfter != null) {
                exchange.getResponseHeaders().set("Retry-After", transientFailureRetryAfter);
            }
        }

        private void propFind(HttpExchange exchange) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (pendingDeletes.containsKey(path)) {
                String retryAfter = deleteConfirmationRetryAfter.getAndSet(null);
                if (retryAfter != null) {
                    exchange.getResponseHeaders().set("Retry-After", retryAfter);
                    send(exchange, 503, "Delete confirmation is temporarily unavailable");
                    return;
                }
            }
            if (deleteConfirmationDelayMillis > 0 && pendingDeletes.containsKey(path)) {
                try {
                    Thread.sleep(deleteConfirmationDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Delete confirmation delay was interrupted", e);
                }
            }
            applyPendingDelete(path);
            if (directories.contains(path) && !exchange.getRequestURI().getPath().endsWith("/")) {
                exchange.getResponseHeaders().set("Location", rawPath(path) + "/");
                redirectCount.incrementAndGet();
                send(exchange, 308, "");
                return;
            }
            if (!directories.contains(path) && !files.containsKey(path)) {
                send(exchange, 404, "Not Found");
                return;
            }

            List<String> resources = new ArrayList<>();
            resources.add(path);
            if ("1".equals(exchange.getRequestHeaders().getFirst("Depth")) && directories.contains(path)) {
                directories.stream()
                        .filter(resource -> !resource.equals(path) && parent(resource).equals(path))
                        .forEach(resources::add);
                files.keySet().stream()
                        .filter(resource -> parent(resource).equals(path))
                        .forEach(resources::add);
            }
            resources.sort(String::compareTo);

            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"")
                    .append(propFindCharset.name())
                    .append("\"?><d:multistatus xmlns:d=\"DAV:\">");
            for (String resource : resources) {
                boolean directory = directories.contains(resource);
                Long contentLength = resource.equals(omitContentLengthPath)
                        ? null
                        : directory ? 0L : files.get(resource).length;
                String etag = etags.get(resource);
                String href = resource.equals(path) && propFindHrefOverride != null
                        ? propFindHrefOverride
                        : rawPath(resource) + (directory ? "/" : "");
                appendPropFindResponse(xml, href, directory, contentLength, etag);
            }
            if (path.equals("/dav/backups")) {
                for (String href : additionalPropFindHrefs) {
                    appendPropFindResponse(xml, href, false, 1L, "\"additional\"");
                }
            }
            xml.append("</d:multistatus>");
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=%s".formatted(propFindCharset.name()));
            byte[] responseBytes = xml.toString().getBytes(propFindCharset);
            if (truncateNextPropFindResponse.compareAndSet(true, false)) {
                exchange.sendResponseHeaders(207, responseBytes.length + 128L);
                exchange.getResponseBody().write(responseBytes, 0,
                        Math.max(1, responseBytes.length / 2));
                exchange.close();
                return;
            }
            send(exchange, 207, responseBytes);
        }

        private void appendPropFindResponse(StringBuilder xml, String href, boolean directory,
                                            Long contentLength, String etag) {
            xml.append("<d:response><d:href>")
                    .append(xmlEscape(href))
                    .append("</d:href>")
                    .append("<d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getcontentlength>999999</d:getcontentlength></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>")
                    .append("<d:propstat><d:prop><d:resourcetype>");
            if (directory) {
                xml.append("<d:collection/>");
            }
            xml.append("</d:resourcetype>");
            if (contentLength != null) {
                xml.append("<d:getcontentlength>")
                        .append(contentLength)
                        .append("</d:getcontentlength>");
            }
            if (etag != null) {
                xml.append("<d:getetag>")
                        .append(xmlEscape(etag))
                        .append("</d:getetag>");
            }
            xml.append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>");
        }

        private void makeCollection(HttpExchange exchange, Integer responseStatusOverride) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (!directories.contains(parent(path))) {
                send(exchange, 409, "Parent does not exist");
                return;
            }
            if (!directories.add(path)) {
                send(exchange, 405, "Already exists");
                return;
            }
            etags.put(path, nextEtag());
            send(exchange, responseStatusOverride == null ? 201 : responseStatusOverride,
                    responseStatusOverride == null ? "" : "Transient failure after mutation");
        }

        private void put(HttpExchange exchange, Integer responseStatusOverride) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (!directories.contains(parent(path))) {
                send(exchange, 409, "Parent does not exist");
                return;
            }
            if (!matchesIfMatch(exchange, path)
                    || ("*".equals(exchange.getRequestHeaders().getFirst("If-None-Match"))
                    && (directories.contains(path) || files.containsKey(path)))) {
                send(exchange, 412, "Upload precondition does not match");
                return;
            }
            lastPutContentLength = exchange.getRequestHeaders().getFirst("Content-Length");
            lastPutTransferEncoding = exchange.getRequestHeaders().getFirst("Transfer-Encoding");
            files.put(path, exchange.getRequestBody().readAllBytes());
            etags.put(path, nextEtag());
            send(exchange, responseStatusOverride == null ? 201 : responseStatusOverride,
                    responseStatusOverride == null ? "" : "Transient failure after mutation");
        }

        private void get(HttpExchange exchange) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            byte[] content = files.get(path);
            if (content == null) {
                send(exchange, 404, "Not Found");
                return;
            }
            if (stallDownloads) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().flush();
                try {
                    Thread.sleep(3_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        }

        private void move(HttpExchange exchange, Integer responseStatusOverride) throws IOException {
            String source = normalize(exchange.getRequestURI().getPath());
            if (!matchesIfMatch(exchange, source)) {
                send(exchange, 412, "ETag does not match");
                return;
            }
            URI destinationUri = URI.create(exchange.getRequestHeaders().getFirst("Destination"));
            String destination = normalize(destinationUri.getPath());
            if ("F".equals(exchange.getRequestHeaders().getFirst("Overwrite"))
                    && (files.containsKey(destination) || directories.contains(destination))) {
                send(exchange, 412, "Destination exists");
                return;
            }
            if (files.containsKey(source)) {
                String sourceEtag = etags.remove(source);
                files.put(destination, files.remove(source));
                if (sourceEtag != null) {
                    etags.put(destination, sourceEtag);
                }
            } else if (directories.contains(source)) {
                moveDirectory(source, destination);
            } else {
                send(exchange, 404, "Not Found");
                return;
            }
            send(exchange, responseStatusOverride == null ? 201 : responseStatusOverride,
                    responseStatusOverride == null ? "" : "Transient failure after mutation");
        }

        private void moveDirectory(String source, String destination) {
            List<String> sourceDirectories = directories.stream()
                    .filter(path -> path.equals(source) || path.startsWith(source + "/"))
                    .toList();
            Map<String, byte[]> sourceFiles = files.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(source + "/"))
                    .collect(ConcurrentHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
            sourceDirectories.forEach(directories::remove);
            sourceFiles.keySet().forEach(files::remove);
            sourceDirectories.forEach(path -> {
                String sourceEtag = etags.remove(path);
                String targetPath = destination + path.substring(source.length());
                directories.add(targetPath);
                if (sourceEtag != null) {
                    etags.put(targetPath, sourceEtag);
                }
            });
            sourceFiles.forEach((path, content) -> {
                String sourceEtag = etags.remove(path);
                String targetPath = destination + path.substring(source.length());
                files.put(targetPath, content);
                if (sourceEtag != null) {
                    etags.put(targetPath, sourceEtag);
                }
            });
        }

        private void delete(HttpExchange exchange, Integer responseStatusOverride) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (!matchesIfMatch(exchange, path)) {
                send(exchange, 412, "ETag does not match");
                return;
            }
            if (!directories.contains(path) && !files.containsKey(path)) {
                send(exchange, 404, "Not Found");
                return;
            }
            if (asyncDelete) {
                pendingDeletes.put(path, 2);
                send(exchange, responseStatusOverride == null ? 202 : responseStatusOverride,
                        responseStatusOverride == null ? "" : "Transient failure after mutation");
            } else {
                remove(path);
                send(exchange, responseStatusOverride == null ? 204 : responseStatusOverride,
                        responseStatusOverride == null ? "" : "Transient failure after mutation");
            }
        }

        private void applyPendingDelete(String path) {
            Integer checksRemaining = pendingDeletes.get(path);
            if (checksRemaining == null) {
                return;
            }
            if (checksRemaining <= 1) {
                pendingDeletes.remove(path);
                remove(path);
            } else {
                pendingDeletes.put(path, checksRemaining - 1);
            }
        }

        private void remove(String path) {
            files.keySet().removeIf(resource -> resource.equals(path) || resource.startsWith(path + "/"));
            directories.removeIf(resource -> resource.equals(path) || resource.startsWith(path + "/"));
            etags.keySet().removeIf(resource -> resource.equals(path) || resource.startsWith(path + "/"));
        }

        private boolean matchesIfMatch(HttpExchange exchange, String path) {
            String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
            return ifMatch == null || ifMatch.equals(etags.get(path));
        }

        private String nextEtag() {
            return "\"etag-%d\"".formatted(etagSequence.incrementAndGet());
        }

        private String parent(String path) {
            int separator = path.lastIndexOf('/');
            return separator <= 0 ? "/" : path.substring(0, separator);
        }

        private String rawPath(String path) {
            try {
                return new URI(null, null, path, null).getRawPath();
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }

        private String xmlEscape(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }

        private String normalize(String path) {
            String normalized = path;
            while (normalized.length() > 1 && normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            return normalized;
        }

        private void send(HttpExchange exchange, int statusCode, String body) throws IOException {
            send(exchange, statusCode, body.getBytes(StandardCharsets.UTF_8));
        }

        private void send(HttpExchange exchange, int statusCode, byte[] bytes) throws IOException {
            if (statusCode == 204 || bytes.length == 0) {
                exchange.sendResponseHeaders(statusCode, -1);
            } else {
                exchange.sendResponseHeaders(statusCode, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }

        private void assertHealthy() {
            assertFalse(authorizationFailed.get(), "WebDAV request did not include the expected Basic Authorization header");
            assertNull(handlerFailure.get(), () -> "WebDAV test server failed: " + handlerFailure.get());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
