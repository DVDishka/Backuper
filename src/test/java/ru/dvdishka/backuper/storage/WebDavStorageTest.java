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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        private final Map<String, Integer> pendingDeletes = new ConcurrentHashMap<>();
        private final AtomicBoolean authorizationFailed = new AtomicBoolean();
        private final AtomicReference<Throwable> handlerFailure = new AtomicReference<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger redirectCount = new AtomicInteger();
        private final AtomicInteger mutationRedirectCount = new AtomicInteger();
        private final List<String> rawPaths = new CopyOnWriteArrayList<>();
        private final List<String> additionalPropFindHrefs = new CopyOnWriteArrayList<>();

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

        private InMemoryWebDavServer(String username, String password) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            expectedAuthorization = "Basic " + Base64.getEncoder().encodeToString(
                    "%s:%s".formatted(username, password).getBytes(StandardCharsets.UTF_8));
            directories.add("/dav");
            directories.add("/dav/backups");
            server.createContext("/dav", this::handle);
        }

        private void start() {
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:%d/dav/".formatted(server.getAddress().getPort());
        }

        private void addDirectory(String path) {
            directories.add(normalize(path));
        }

        private void addFile(String path, byte[] content) {
            files.put(normalize(path), content.clone());
        }

        private void handle(HttpExchange exchange) throws IOException {
            requestCount.incrementAndGet();
            rawPaths.add(exchange.getRequestURI().getRawPath());
            try {
                if (!expectedAuthorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    authorizationFailed.set(true);
                    send(exchange, 401, "Unauthorized");
                    return;
                }
                if (redirectLocation != null && "PROPFIND".equals(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Location", redirectLocation);
                    redirectCount.incrementAndGet();
                    send(exchange, 302, "");
                    return;
                }
                if (mutationRedirectMethod != null
                        && mutationRedirectMethod.equals(exchange.getRequestMethod())
                        && mutationRedirectCount.compareAndSet(0, 1)) {
                    exchange.getRequestBody().readAllBytes();
                    exchange.getResponseHeaders().set("Location", mutationRedirectLocation);
                    send(exchange, 308, "");
                    return;
                }

                switch (exchange.getRequestMethod()) {
                    case "PROPFIND" -> propFind(exchange);
                    case "MKCOL" -> makeCollection(exchange);
                    case "PUT" -> put(exchange);
                    case "GET" -> get(exchange);
                    case "MOVE" -> move(exchange);
                    case "DELETE" -> delete(exchange);
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

        private void propFind(HttpExchange exchange) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
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
                String href = resource.equals(path) && propFindHrefOverride != null
                        ? propFindHrefOverride
                        : rawPath(resource) + (directory ? "/" : "");
                appendPropFindResponse(xml, href, directory, contentLength);
            }
            if (path.equals("/dav/backups")) {
                for (String href : additionalPropFindHrefs) {
                    appendPropFindResponse(xml, href, false, 1L);
                }
            }
            xml.append("</d:multistatus>");
            exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=%s".formatted(propFindCharset.name()));
            send(exchange, 207, xml.toString().getBytes(propFindCharset));
        }

        private void appendPropFindResponse(StringBuilder xml, String href, boolean directory, Long contentLength) {
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
            xml.append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>");
        }

        private void makeCollection(HttpExchange exchange) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (!directories.contains(parent(path))) {
                send(exchange, 409, "Parent does not exist");
                return;
            }
            if (!directories.add(path)) {
                send(exchange, 405, "Already exists");
                return;
            }
            send(exchange, 201, "");
        }

        private void put(HttpExchange exchange) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (!directories.contains(parent(path))) {
                send(exchange, 409, "Parent does not exist");
                return;
            }
            lastPutContentLength = exchange.getRequestHeaders().getFirst("Content-Length");
            lastPutTransferEncoding = exchange.getRequestHeaders().getFirst("Transfer-Encoding");
            files.put(path, exchange.getRequestBody().readAllBytes());
            send(exchange, 201, "");
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

        private void move(HttpExchange exchange) throws IOException {
            String source = normalize(exchange.getRequestURI().getPath());
            URI destinationUri = URI.create(exchange.getRequestHeaders().getFirst("Destination"));
            String destination = normalize(destinationUri.getPath());
            if ("F".equals(exchange.getRequestHeaders().getFirst("Overwrite"))
                    && (files.containsKey(destination) || directories.contains(destination))) {
                send(exchange, 412, "Destination exists");
                return;
            }
            if (files.containsKey(source)) {
                files.put(destination, files.remove(source));
            } else if (directories.contains(source)) {
                moveDirectory(source, destination);
            } else {
                send(exchange, 404, "Not Found");
                return;
            }
            send(exchange, 201, "");
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
            sourceDirectories.forEach(path -> directories.add(destination + path.substring(source.length())));
            sourceFiles.forEach((path, content) -> files.put(destination + path.substring(source.length()), content));
        }

        private void delete(HttpExchange exchange) throws IOException {
            String path = normalize(exchange.getRequestURI().getPath());
            if (!directories.contains(path) && !files.containsKey(path)) {
                send(exchange, 404, "Not Found");
                return;
            }
            if (asyncDelete) {
                pendingDeletes.put(path, 2);
                send(exchange, 202, "");
            } else {
                remove(path);
                send(exchange, 204, "");
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
