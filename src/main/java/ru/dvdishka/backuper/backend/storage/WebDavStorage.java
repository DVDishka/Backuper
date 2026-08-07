package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.WebDavConfig;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressInputStream;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class WebDavStorage implements PathStorage {

    private static final String PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:propfind xmlns:d="DAV:">
              <d:prop>
                <d:resourcetype/>
                <d:getcontentlength/>
              </d:prop>
            </d:propfind>
            """;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_ERROR_BODY_BYTES = 512;
    private static final long DELETE_CONFIRMATION_INTERVAL_MILLIS = 100L;
    private static final long NO_DEADLINE = Long.MIN_VALUE;

    @Setter
    private String id;
    private final WebDavConfig config;
    private final BackupManager backupManager;
    private final URI endpoint;
    private final String endpointValidationError;
    private final String authorizationValidationError;
    private final String authorizationHeader;
    private final WebDavProtocolLogger protocolLogger;
    private volatile HttpClient httpClient;
    private volatile ScheduledExecutorService responseTimeoutExecutor;
    private volatile boolean destroyed;

    public WebDavStorage(WebDavConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
        URI parsedEndpoint = null;
        String validationError = null;
        try {
            parsedEndpoint = parseEndpoint(config.getUrl());
        } catch (IllegalArgumentException e) {
            validationError = e.getMessage();
        }
        this.endpoint = parsedEndpoint;
        this.endpointValidationError = validationError;
        this.authorizationValidationError = config.getUsername() != null && config.getUsername().contains(":")
                ? "WebDAV Basic authentication username cannot contain ':'"
                : null;
        this.authorizationHeader = authorizationValidationError == null
                ? createAuthorizationHeader(config.getUsername(), config.getPassword())
                : null;
        this.protocolLogger = config.isProtocolLogging() ? new WebDavProtocolLogger(config.getId()) : null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public StorageType getType() {
        return StorageType.WEBDAV;
    }

    @Override
    public WebDavConfig getConfig() {
        return config;
    }

    @Override
    public BackupManager getBackupManager() {
        return backupManager;
    }

    @Override
    public boolean checkConnection() {
        return checkConnection(null);
    }

    @Override
    public boolean checkConnection(CommandSender sender) {
        try {
            if (!stat(config.getBackupsFolder()).directory()) {
                throw new StorageMethodException(this,
                        "WebDAV backups folder is not a directory: %s".formatted(config.getBackupsFolder()));
            }
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the WebDAV server", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        PropFindResult result = propFind(path, true, "1", false, "list directory");
        WebDavResource requestedResource = requestedResource(result)
                .orElseThrow(() -> new StorageMethodException(this,
                        "WebDAV server returned no directory metadata for \"%s\"".formatted(path)));
        if (!requestedResource.directory()) {
            throw new StorageMethodException(this, "WebDAV resource is not a directory: %s".formatted(path));
        }
        String requestedPath = normalizeUriPath(result.uri().getPath());
        return result.resources().stream()
                .filter(resource -> isDirectChild(requestedPath, resource.path()))
                .map(WebDavResource::name)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        return exists(path, NO_DEADLINE);
    }

    private boolean exists(String path, long deadlineNanos) throws StorageMethodException, StorageConnectionException {
        PropFindResult result = propFind(path, false, "0", true, "check resource existence", deadlineNanos);
        if (result.statusCode() == 404) {
            return false;
        }
        if (requestedResource(result).isEmpty()) {
            throw new StorageMethodException(this, "WebDAV server returned no resource metadata for \"%s\"".formatted(path));
        }
        return true;
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        return !stat(path).directory();
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        WebDavResource resource = stat(path);
        if (!resource.directory()) {
            if (resource.contentLength() == null) {
                throw new StorageMethodException(this,
                        "WebDAV server did not return getcontentlength for file: %s".formatted(path));
            }
            return resource.contentLength();
        }

        long size = 0;
        for (String child : ls(path)) {
            try {
                size = Math.addExact(size, getDirByteSize(resolve(path, child)));
            } catch (ArithmeticException e) {
                throw new StorageMethodException(this, "WebDAV directory size exceeds the supported range: %s".formatted(path), e);
            }
        }
        return size;
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        String path = resolve(parentDir, newDirName);
        URI uri = resourceUri(path, true);
        executeFollowingRedirects(uri, redirectUri -> newRequest(redirectUri)
                .method("MKCOL", HttpRequest.BodyPublishers.noBody())
                .build(), "create directory", true, 200, 201, 204);
        if (!stat(path).directory()) {
            throw new StorageMethodException(this, "Directory creation verification failed: %s".formatted(path));
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener)
            throws StorageLimitException, StorageMethodException, StorageConnectionException {
        String path = resolve(targetParentDir, newFileName);
        URI uri = resourceUri(path, false);
        Path bufferedUpload = null;
        try {
            HttpRequest.BodyPublisher body;
            if (config.isBufferUploadsToDisk()) {
                bufferedUpload = Files.createTempFile("backuper-webdav-", ".upload");
                Files.copy(sourceStream, bufferedUpload, StandardCopyOption.REPLACE_EXISTING);
                body = fixedLengthBodyPublisher(bufferedUpload, progressListener);
            } else {
                body = streamingBodyPublisher(sourceStream, progressListener);
            }

            HttpRequest.BodyPublisher requestBody = body;
            executeFollowingRedirects(uri, redirectUri -> newRequest(redirectUri)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(requestBody)
                    .build(), "upload file", config.isBufferUploadsToDisk(), 200, 201, 204);
        } catch (IOException e) {
            throw new StorageMethodException(this, "Failed to buffer WebDAV upload", e);
        } finally {
            deleteTemporaryUpload(bufferedUpload);
        }

        if (stat(path).directory()) {
            throw new StorageMethodException(this, "Upload verification failed: %s".formatted(path));
        }
    }

    @Override
    public InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        URI uri = resourceUri(sourcePath, false);
        HttpResponse<InputStream> response = sendFollowingRedirects(uri,
                redirectUri -> newRequest(redirectUri).GET().build(), "download file");
        if (response.statusCode() != 200) {
            try (InputStream body = timedResponseBody(response.body(), "download file")) {
                throw statusException("download file", response.statusCode(), body);
            } catch (IOException e) {
                throw connectionException("download file", e);
            }
        }
        return new StorageProgressInputStream(timedResponseBody(response.body(), "download file"), progressListener);
    }

    @Override
    public void downloadCompleted() {
        // The response body is completed when the returned input stream is closed.
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        boolean directory = stat(path).directory();
        URI uri = resourceUri(path, directory);
        int statusCode = executeFollowingRedirects(uri, redirectUri -> newRequest(redirectUri)
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .build(), "delete resource", true, 200, 202, 204);

        if (statusCode == 202) {
            if (config.getDeleteConfirmationTimeoutSeconds() > 0) {
                waitUntilDeleted(path, Duration.ofSeconds(config.getDeleteConfirmationTimeoutSeconds()));
            }
        } else if (exists(path)) {
            throw new StorageMethodException(this, "Delete verification failed: %s".formatted(path));
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        boolean directory = stat(path).directory();
        String targetPath = resolve(getParentPath(path), newFileName);
        URI sourceUri = resourceUri(path, directory);
        URI targetUri = resourceUri(targetPath, directory);
        executeFollowingRedirects(sourceUri, redirectUri -> newRequest(redirectUri)
                .header("Destination", targetUri.toASCIIString())
                .header("Overwrite", "F")
                .method("MOVE", HttpRequest.BodyPublishers.noBody())
                .build(), "rename resource", true, 201, 204);
        if (!exists(targetPath) || exists(path)) {
            throw new StorageMethodException(this, "Rename verification failed from \"%s\" to \"%s\"".formatted(path, targetPath));
        }
    }

    @Override
    public int getStorageSpeedMultiplier() {
        return 8;
    }

    @Override
    public synchronized void destroy() {
        destroyed = true;
        if (responseTimeoutExecutor != null) {
            responseTimeoutExecutor.shutdownNow();
        }
        if (httpClient != null) {
            httpClient.shutdownNow();
        }
        if (protocolLogger != null) {
            protocolLogger.close();
        }
    }

    private WebDavResource stat(String path) {
        PropFindResult result = propFind(path, false, "0", true, "read resource metadata");
        if (result.statusCode() == 404) {
            throw statusException("read resource metadata", 404, InputStream.nullInputStream());
        }
        return requestedResource(result)
                .orElseThrow(() -> new StorageMethodException(this, "WebDAV server returned no resource metadata for \"%s\"".formatted(path)));
    }

    private Optional<WebDavResource> requestedResource(PropFindResult result) {
        String requestedPath = normalizeUriPath(result.uri().getPath());
        Optional<WebDavResource> exactResource = result.resources().stream()
                .filter(resource -> normalizeUriPath(resource.path()).equals(requestedPath))
                .findFirst();
        return exactResource;
    }

    private PropFindResult propFind(String path, boolean directory, String depth, boolean retryAsDirectory, String action) {
        return propFind(path, directory, depth, retryAsDirectory, action, NO_DEADLINE);
    }

    private PropFindResult propFind(String path, boolean directory, String depth, boolean retryAsDirectory,
                                    String action, long deadlineNanos) {
        URI uri = resourceUri(path, directory);
        HttpResponse<InputStream> response = sendPropFind(uri, depth, action, deadlineNanos);
        if (response.statusCode() == 404 && retryAsDirectory && !directory) {
            closeResponseBody(response, action);
            uri = resourceUri(path, true);
            response = sendPropFind(uri, depth, action, deadlineNanos);
        }

        URI responseUri = response.uri();
        try (InputStream body = timedResponseBody(response.body(), action, deadlineNanos)) {
            if (response.statusCode() == 404) {
                return new PropFindResult(responseUri, 404, List.of());
            }
            requireStatus(action, response.statusCode(), body, 200, 207);
            return new PropFindResult(responseUri, response.statusCode(), parseResources(body, responseUri, action));
        } catch (IOException e) {
            throw connectionException(action, e);
        }
    }

    private HttpResponse<InputStream> sendPropFind(URI uri, String depth, String action, long deadlineNanos) {
        return sendFollowingRedirects(uri, redirectUri -> newRequest(redirectUri, requestTimeout(deadlineNanos))
                .header("Depth", depth)
                .header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", HttpRequest.BodyPublishers.ofString(PROPFIND_BODY))
                .build(), action);
    }

    private HttpResponse<InputStream> sendFollowingRedirects(URI initialUri, Function<URI, HttpRequest> requestFactory, String action) {
        return sendFollowingRedirects(initialUri, requestFactory, action, true, false);
    }

    private HttpResponse<InputStream> sendFollowingRedirects(URI initialUri, Function<URI, HttpRequest> requestFactory,
                                                              String action, boolean replayable, boolean methodPreservingOnly) {
        Set<URI> visitedUris = new HashSet<>();
        URI currentUri = initialUri;
        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            if (!visitedUris.add(currentUri)) {
                throw new StorageMethodException(this, "WebDAV redirect loop detected at %s".formatted(currentUri));
            }

            HttpResponse<InputStream> response = send(requestFactory.apply(currentUri), action);
            boolean redirect = methodPreservingOnly
                    ? isMethodPreservingRedirect(response.statusCode())
                    : isRedirect(response.statusCode());
            if (!redirect) {
                return response;
            }
            if (!replayable) {
                closeResponseBody(response, action);
                throw new StorageMethodException(this,
                        "Cannot safely follow a WebDAV redirect while streaming an upload. Configure the canonical URL or enable http.bufferUploadsToDisk");
            }
            if (redirectCount == MAX_REDIRECTS) {
                closeResponseBody(response, action);
                throw new StorageMethodException(this, "WebDAV server exceeded the maximum redirect count");
            }

            Optional<String> location = response.headers().firstValue("Location");
            if (location.isEmpty()) {
                closeResponseBody(response, action);
                throw new StorageMethodException(this, "WebDAV redirect response did not include a Location header");
            }
            URI redirectReference;
            URI redirectUri;
            try {
                redirectReference = URI.create(location.get());
                redirectUri = currentUri.resolve(redirectReference).normalize();
            } catch (IllegalArgumentException e) {
                closeResponseBody(response, action);
                throw new StorageMethodException(this, "WebDAV server returned an invalid redirect URI");
            }
            closeResponseBody(response, action);
            if (hasDotPathSegment(redirectReference) || hasEncodedPathSeparator(redirectReference)) {
                throw new StorageConnectionException(this, "Refusing unsafe WebDAV redirect path");
            }
            validateRedirect(redirectUri);
            currentUri = redirectUri;
        }
        throw new StorageMethodException(this, "WebDAV server exceeded the maximum redirect count");
    }

    private HttpRequest.Builder newRequest(URI uri) {
        return newRequest(uri, requestTimeout(NO_DEADLINE));
    }

    private HttpRequest.Builder newRequest(URI uri, Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/xml, */*")
                .header("User-Agent", "Backuper-WebDAV");
        if (timeout != null) {
            builder.timeout(timeout);
        }
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        return builder;
    }

    private HttpResponse<InputStream> send(HttpRequest request, String action) {
        HttpClient client = httpClient();
        logRequest(request);
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            logResponse(request, response.statusCode());
            return response;
        } catch (IOException e) {
            throw connectionException(action, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw connectionException(action, e);
        } catch (UncheckedIOException e) {
            throw connectionException(action, e.getCause());
        }
    }

    private int executeFollowingRedirects(URI initialUri, Function<URI, HttpRequest> requestFactory,
                                          String action, boolean replayable, int... expectedStatuses) {
        HttpResponse<InputStream> response = sendFollowingRedirects(
                initialUri, requestFactory, action, replayable, true);
        try (InputStream body = timedResponseBody(response.body(), action)) {
            requireStatus(action, response.statusCode(), body, expectedStatuses);
            return response.statusCode();
        } catch (IOException e) {
            throw connectionException(action, e);
        }
    }

    private void requireStatus(String action, int statusCode, InputStream responseBody, int... expectedStatuses) {
        for (int expectedStatus : expectedStatuses) {
            if (statusCode == expectedStatus) {
                return;
            }
        }
        throw statusException(action, statusCode, responseBody);
    }

    private RuntimeException statusException(String action, int statusCode, InputStream responseBody) {
        String details = readResponseDetails(responseBody);
        String message = "Failed to %s. WebDAV server returned HTTP %d%s".formatted(
                action, statusCode, details.isEmpty() ? "" : ": " + details);
        if (statusCode == 401 || statusCode == 403) {
            return new StorageConnectionException(this, message);
        }
        if (statusCode == 507) {
            return new StorageLimitException(this, message);
        }
        if (statusCode == 408 || statusCode == 429 || statusCode >= 500) {
            return new StorageConnectionException(this, message);
        }
        return new StorageMethodException(this, message);
    }

    private String readResponseDetails(InputStream responseBody) {
        try {
            byte[] bytes = responseBody.readNBytes(MAX_ERROR_BODY_BYTES + 1);
            boolean truncated = bytes.length > MAX_ERROR_BODY_BYTES;
            int length = Math.min(bytes.length, MAX_ERROR_BODY_BYTES);
            String details = new String(bytes, 0, length, StandardCharsets.UTF_8).strip();
            return truncated ? details + "..." : details;
        } catch (IOException e) {
            return "";
        }
    }

    private StorageConnectionException connectionException(String action, Exception exception) {
        String reason = exception instanceof HttpTimeoutException ? "request timed out"
                : exception instanceof ConnectException ? "connection failed"
                : exception instanceof UnknownHostException ? "host could not be resolved"
                : "request failed";
        return new StorageConnectionException(this, "Failed to %s: WebDAV %s".formatted(action, reason), exception);
    }

    private URI resourceUri(String path, boolean directory) {
        if (endpointValidationError != null) {
            throw new StorageConnectionException(this, endpointValidationError);
        }
        if (endpoint == null) {
            throw new StorageConnectionException(this, "WebDAV URL is not configured");
        }
        if (authorizationValidationError != null) {
            throw new StorageConnectionException(this, authorizationValidationError);
        }
        if (authorizationHeader != null && "http".equalsIgnoreCase(endpoint.getScheme()) && !config.isAllowInsecureHttp()) {
            throw new StorageConnectionException(this,
                    "Refusing to send WebDAV Basic credentials over unencrypted HTTP. Use HTTPS or set auth.allowInsecureHttp to true");
        }

        String endpointPath = endpoint.getRawPath();
        if (endpointPath == null || endpointPath.isEmpty()) {
            endpointPath = "/";
        }
        if (!endpointPath.endsWith("/")) {
            endpointPath += "/";
        }

        List<String> encodedSegments = new ArrayList<>();
        if (path != null) {
            for (String segment : path.replace('\\', '/').split("/")) {
                if (segment.isEmpty() || segment.equals(".")) {
                    continue;
                }
                if (segment.equals("..")) {
                    throw new StorageMethodException(this, "WebDAV paths cannot contain '..': %s".formatted(path));
                }
                encodedSegments.add(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }

        String rawPath = endpointPath + String.join("/", encodedSegments);
        if (directory && !rawPath.endsWith("/")) {
            rawPath += "/";
        }
        return URI.create("%s://%s%s".formatted(endpoint.getScheme(), endpoint.getRawAuthority(), rawPath));
    }

    private void validateRedirect(URI redirectUri) {
        if (!sameOrigin(endpoint, redirectUri) || redirectUri.getUserInfo() != null
                || redirectUri.getRawQuery() != null || redirectUri.getRawFragment() != null) {
            throw new StorageConnectionException(this, "Refusing unsafe WebDAV redirect");
        }

        if (!isWithinConfiguredRoot(redirectUri)) {
            throw new StorageConnectionException(this, "Refusing WebDAV redirect outside the configured root");
        }
        if (hasDotPathSegment(redirectUri) || hasEncodedPathSeparator(redirectUri)) {
            throw new StorageConnectionException(this, "Refusing WebDAV redirect containing a relative path segment");
        }
    }

    private URI resolveResourceHref(URI requestUri, String href) {
        URI hrefReference;
        URI resourceUri;
        try {
            hrefReference = URI.create(href);
            resourceUri = requestUri.resolve(hrefReference).normalize();
        } catch (IllegalArgumentException e) {
            throw new StorageMethodException(this, "WebDAV server returned an invalid resource href");
        }

        if (hasDotPathSegment(hrefReference) || hasEncodedPathSeparator(hrefReference)
                || !sameOrigin(endpoint, resourceUri) || resourceUri.getUserInfo() != null
                || resourceUri.getRawQuery() != null || resourceUri.getRawFragment() != null
                || !isWithinConfiguredRoot(resourceUri) || hasDotPathSegment(resourceUri)
                || hasEncodedPathSeparator(resourceUri)) {
            throw new StorageMethodException(this, "WebDAV server returned an unsafe resource href");
        }
        return resourceUri;
    }

    private boolean isWithinConfiguredRoot(URI uri) {
        if (hasEncodedPathSeparator(endpoint) || hasEncodedPathSeparator(uri)) {
            return false;
        }
        String endpointPath = normalizeUriPath(endpoint.getPath());
        String resourcePath = normalizeUriPath(uri.getPath());
        return endpointPath.equals("/") || resourcePath.equals(endpointPath)
                || resourcePath.startsWith(endpointPath + "/");
    }

    private boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 307 || statusCode == 308;
    }

    private boolean isMethodPreservingRedirect(int statusCode) {
        return statusCode == 307 || statusCode == 308;
    }

    private List<WebDavResource> parseResources(InputStream responseBody, URI requestUri, String action) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        setXmlPropertyIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setXmlPropertyIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML entities are disabled");
        });

        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(responseBody);
            List<WebDavResource> resources = new ArrayList<>();
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "response".equals(reader.getLocalName())) {
                    WebDavResource resource = parseResource(reader, requestUri);
                    if (resource != null) {
                        resources.add(resource);
                    }
                }
            }
            return resources;
        } catch (XMLStreamException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof HttpTimeoutException timeoutException) {
                    throw connectionException(action, timeoutException);
                }
                cause = cause.getCause();
            }
            throw new StorageMethodException(this, "Failed to parse WebDAV PROPFIND response", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                }
            }
        }
    }

    private WebDavResource parseResource(XMLStreamReader reader, URI requestUri) throws XMLStreamException {
        String href = null;
        WebDavProperties directProperties = null;
        WebDavProperties successfulProperties = null;
        boolean hasPropStat = false;

        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "href" -> href = reader.getElementText().strip();
                    case "propstat" -> {
                        hasPropStat = true;
                        WebDavPropStat propStat = parsePropStat(reader);
                        if (isSuccessfulStatus(propStat.status())) {
                            successfulProperties = mergeProperties(successfulProperties, propStat.properties());
                        }
                    }
                    case "prop" -> directProperties = parseProperties(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "response".equals(reader.getLocalName())) {
                break;
            }
        }

        WebDavProperties properties = hasPropStat ? successfulProperties : directProperties;
        if (href == null || href.isBlank() || properties == null) {
            return null;
        }
        URI resourceUri = resolveResourceHref(requestUri, href);
        String resourcePath = resourceUri.getPath();
        return new WebDavResource(resourcePath, fileName(resourcePath), properties.directory(), properties.contentLength());
    }

    private WebDavPropStat parsePropStat(XMLStreamReader reader) throws XMLStreamException {
        String status = null;
        WebDavProperties properties = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (reader.getLocalName()) {
                    case "status" -> status = reader.getElementText().strip();
                    case "prop" -> properties = parseProperties(reader);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "propstat".equals(reader.getLocalName())) {
                break;
            }
        }
        return new WebDavPropStat(status, properties);
    }

    private WebDavProperties parseProperties(XMLStreamReader reader) throws XMLStreamException {
        boolean directory = false;
        Long contentLength = null;
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if ("collection".equals(reader.getLocalName())) {
                    directory = true;
                } else if ("getcontentlength".equals(reader.getLocalName())) {
                    contentLength = parseContentLength(reader.getElementText().strip());
                }
            } else if (event == XMLStreamConstants.END_ELEMENT && "prop".equals(reader.getLocalName())) {
                break;
            }
        }
        return new WebDavProperties(directory, contentLength);
    }

    private WebDavProperties mergeProperties(WebDavProperties first, WebDavProperties second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        Long contentLength = second.contentLength() == null ? first.contentLength() : second.contentLength();
        return new WebDavProperties(first.directory() || second.directory(), contentLength);
    }

    private void setXmlPropertyIfSupported(XMLInputFactory factory, String property, Object value) {
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private boolean isSuccessfulStatus(String status) {
        return status != null && status.matches(".*\\s2\\d\\d(?:\\s.*)?");
    }

    private Long parseContentLength(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long contentLength = Long.parseLong(value);
            if (contentLength < 0) {
                throw new NumberFormatException("value must be non-negative");
            }
            return contentLength;
        } catch (NumberFormatException e) {
            throw new StorageMethodException(this, "Invalid WebDAV getcontentlength value: %s".formatted(value), e);
        }
    }

    private HttpRequest.BodyPublisher fixedLengthBodyPublisher(Path path, StorageProgressListener progressListener) throws IOException {
        long contentLength = Files.size(path);
        if (contentLength == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        StorageProgressListener cappedProgressListener = new CappedProgressListener(progressListener, contentLength);
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return new StorageProgressInputStream(Files.newInputStream(path), cappedProgressListener);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return HttpRequest.BodyPublishers.fromPublisher(body, contentLength);
    }

    private HttpRequest.BodyPublisher streamingBodyPublisher(InputStream sourceStream, StorageProgressListener progressListener) {
        AtomicBoolean opened = new AtomicBoolean();
        return HttpRequest.BodyPublishers.ofInputStream(() -> {
            if (!opened.compareAndSet(false, true)) {
                throw new UncheckedIOException(new IOException("WebDAV streaming upload cannot be replayed"));
            }
            return new StorageProgressInputStream(sourceStream, progressListener);
        });
    }

    private void deleteTemporaryUpload(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Backuper.getInstance().getLogManager().devWarn("Failed to delete temporary WebDAV upload file: %s".formatted(path));
            Backuper.getInstance().getLogManager().devWarn(e);
        }
    }

    private void waitUntilDeleted(String path, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            throwIfDeleteConfirmationTimedOut(path, timeout, deadline);
            try {
                if (!exists(path, deadline)) {
                    return;
                }
            } catch (StorageConnectionException e) {
                if (deadline - System.nanoTime() <= 0) {
                    throw deleteConfirmationTimeout(path, timeout);
                }
                throw e;
            }

            long remainingNanos = deadline - System.nanoTime();
            throwIfDeleteConfirmationTimedOut(path, timeout, deadline);
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(
                        TimeUnit.MILLISECONDS.toNanos(DELETE_CONFIRMATION_INTERVAL_MILLIS), remainingNanos));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw connectionException("confirm resource deletion", e);
            }
        }
    }

    private void throwIfDeleteConfirmationTimedOut(String path, Duration timeout, long deadline) {
        if (deadline - System.nanoTime() <= 0) {
            throw deleteConfirmationTimeout(path, timeout);
        }
    }

    private StorageMethodException deleteConfirmationTimeout(String path, Duration timeout) {
        return new StorageMethodException(this,
                "WebDAV server accepted deletion, but completion was not confirmed within %d seconds. The deletion may still complete: %s"
                        .formatted(timeout.toSeconds(), path));
    }

    private void closeResponseBody(HttpResponse<InputStream> response, String action) {
        try {
            response.body().close();
        } catch (IOException e) {
            throw connectionException(action, e);
        }
    }

    private InputStream timedResponseBody(InputStream responseBody, String action) {
        return timedResponseBody(responseBody, action, NO_DEADLINE);
    }

    private InputStream timedResponseBody(InputStream responseBody, String action, long deadlineNanos) {
        Duration timeout = requestTimeout(deadlineNanos);
        if (timeout == null) {
            return responseBody;
        }
        return new DeadlineInputStream(responseBody, responseTimeoutExecutor(), timeout, action);
    }

    private Duration requestTimeout(long deadlineNanos) {
        Duration configuredTimeout = config.getRequestTimeoutSeconds() > 0
                ? Duration.ofSeconds(config.getRequestTimeoutSeconds())
                : null;
        if (deadlineNanos == NO_DEADLINE) {
            return configuredTimeout;
        }

        Duration remainingTimeout = Duration.ofNanos(Math.max(1L, deadlineNanos - System.nanoTime()));
        if (configuredTimeout == null || remainingTimeout.compareTo(configuredTimeout) < 0) {
            return remainingTimeout;
        }
        return configuredTimeout;
    }

    private synchronized HttpClient httpClient() {
        if (destroyed) {
            throw new StorageConnectionException(this, "WebDAV storage has been destroyed");
        }
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }
        return httpClient;
    }

    private synchronized ScheduledExecutorService responseTimeoutExecutor() {
        if (destroyed) {
            throw new StorageConnectionException(this, "WebDAV storage has been destroyed");
        }
        if (responseTimeoutExecutor == null) {
            ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
                Thread thread = new Thread(runnable, "Backuper-WebDAV-timeout-%s".formatted(config.getId()));
                thread.setDaemon(true);
                return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
            responseTimeoutExecutor = executor;
        }
        return responseTimeoutExecutor;
    }

    private String fileName(String path) {
        String normalized = normalizeUriPath(path);
        int separator = normalized.lastIndexOf('/');
        return separator == -1 ? normalized : normalized.substring(separator + 1);
    }

    private boolean isDirectChild(String requestedPath, String resourcePath) {
        String normalizedResourcePath = normalizeUriPath(resourcePath);
        if (normalizedResourcePath.equals(requestedPath)) {
            return false;
        }
        int separator = normalizedResourcePath.lastIndexOf('/');
        String parentPath = separator <= 0 ? "/" : normalizedResourcePath.substring(0, separator);
        return parentPath.equals(requestedPath);
    }

    private String normalizeUriPath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String normalized = path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean hasDotPathSegment(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasEncodedPathSeparator(URI uri) {
        String rawPath = uri.getRawPath();
        if (rawPath == null) {
            return false;
        }
        for (int index = 0; index + 2 < rawPath.length(); index++) {
            if (rawPath.charAt(index) != '%') {
                continue;
            }
            int high = Character.digit(rawPath.charAt(index + 1), 16);
            int low = Character.digit(rawPath.charAt(index + 2), 16);
            int value = high < 0 || low < 0 ? -1 : high * 16 + low;
            if (value == '/' || value == '\\') {
                return true;
            }
        }
        return false;
    }

    private void logRequest(HttpRequest request) {
        if (protocolLogger != null) {
            protocolLogger.logRequest(request.method(), request.uri().toASCIIString());
        }
    }

    private void logResponse(HttpRequest request, int statusCode) {
        if (protocolLogger != null) {
            protocolLogger.logResponse(request.method(), request.uri().toASCIIString(), statusCode);
        }
    }

    private static URI parseEndpoint(String value) {
        try {
            String normalizedValue = value == null ? "" : value.strip();
            if (normalizedValue.isEmpty()) {
                return null;
            }
            URI endpoint = URI.create(normalizedValue);
            if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                    || hasDotPathSegment(endpoint) || hasEncodedPathSeparator(endpoint)) {
                throw new IllegalArgumentException("WebDAV URL must be an HTTP(S) URL without credentials, query, or fragment");
            }
            return endpoint.normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid WebDAV URL; expected an HTTP(S) URL without credentials, query, fragment, relative path segments, or encoded separators");
        }
    }

    private static String createAuthorizationHeader(String username, String password) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        String credentials = "%s:%s".formatted(username, password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static class DeadlineInputStream extends InputStream {

        private final InputStream inputStream;
        private final String action;
        private final ScheduledFuture<?> timeoutTask;
        private volatile boolean timedOut;

        private DeadlineInputStream(InputStream inputStream, ScheduledExecutorService executor, Duration timeout, String action) {
            this.inputStream = inputStream;
            this.action = action;
            this.timeoutTask = executor.schedule(() -> {
                timedOut = true;
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int read() throws IOException {
            try {
                int value = inputStream.read();
                checkTimedOut();
                if (value == -1) {
                    timeoutTask.cancel(false);
                }
                return value;
            } catch (IOException e) {
                throw timeoutException(e);
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                int bytesRead = inputStream.read(bytes, offset, length);
                checkTimedOut();
                if (bytesRead == -1) {
                    timeoutTask.cancel(false);
                }
                return bytesRead;
            } catch (IOException e) {
                throw timeoutException(e);
            }
        }

        @Override
        public int available() throws IOException {
            return inputStream.available();
        }

        @Override
        public void close() throws IOException {
            timeoutTask.cancel(false);
            inputStream.close();
        }

        private void checkTimedOut() throws HttpTimeoutException {
            if (timedOut) {
                throw new HttpTimeoutException("WebDAV %s timed out while reading the response body".formatted(action));
            }
        }

        private IOException timeoutException(IOException exception) {
            if (!timedOut || exception instanceof HttpTimeoutException) {
                return exception;
            }
            HttpTimeoutException timeoutException = new HttpTimeoutException(
                    "WebDAV %s timed out while reading the response body".formatted(action));
            timeoutException.initCause(exception);
            return timeoutException;
        }
    }

    private static class CappedProgressListener implements StorageProgressListener {

        private final StorageProgressListener progressListener;
        private final long maximumIncrement;
        private final AtomicLong forwardedProgress = new AtomicLong();

        private CappedProgressListener(StorageProgressListener progressListener, long maximumIncrement) {
            this.progressListener = progressListener;
            this.maximumIncrement = maximumIncrement;
        }

        @Override
        public long getCurrentProgress() {
            return progressListener.getCurrentProgress();
        }

        @Override
        public long getMaxProgress() {
            return progressListener.getMaxProgress();
        }

        @Override
        public void incrementProgress(long value) {
            while (value > 0) {
                long current = forwardedProgress.get();
                long increment = Math.min(value, maximumIncrement - current);
                if (increment <= 0) {
                    return;
                }
                if (forwardedProgress.compareAndSet(current, current + increment)) {
                    progressListener.incrementProgress(increment);
                    return;
                }
            }
        }
    }

    private record PropFindResult(URI uri, int statusCode, List<WebDavResource> resources) {
    }

    private record WebDavResource(String path, String name, boolean directory, Long contentLength) {
    }

    private record WebDavProperties(boolean directory, Long contentLength) {
    }

    private record WebDavPropStat(String status, WebDavProperties properties) {
    }
}
