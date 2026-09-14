package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.S3Config;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressInputStream;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class S3Storage implements PathStorage {

    @Setter
    private String id;
    private final S3Config config;
    private final BackupManager backupManager;
    private final S3Client s3Client;
    private final StorageProtocolLogger protocolLogger;

    public S3Storage(S3Config config) {
        this(config, null);
    }

    public S3Storage(S3Config config, S3Client s3Client) {
        this.config = config;
        this.backupManager = new BackupManager(this);
        this.protocolLogger = config.isProtocolLogging() ? new StorageProtocolLogger(config.getId() != null ? config.getId() : "s3") : null;
        if (s3Client != null) {
            this.s3Client = s3Client;
        } else {
            this.s3Client = initS3Client();
        }
    }

    private S3Client initS3Client() {
        if (config.getAccessKeyId() == null || config.getAccessKeyId().isBlank() ||
                config.getSecretAccessKey() == null || config.getSecretAccessKey().isBlank()) {
            Backuper.getInstance().getLogManager().warn("S3 storage \"%s\" is missing accessKeyId or secretAccessKey!".formatted(config.getId()));
            return null;
        }

        try {
            AwsCredentials credentials = (config.getSessionToken() != null && !config.getSessionToken().isBlank())
                    ? AwsSessionCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey(), config.getSessionToken())
                    : AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey());

            int timeoutSeconds = config.getRequestTimeoutSeconds() <= 0 ? 3600 : config.getRequestTimeoutSeconds();

            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(config.getRegion().isBlank() ? "us-east-1" : config.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .httpClientBuilder(UrlConnectionHttpClient.builder()
                            .socketTimeout(Duration.ofSeconds(timeoutSeconds))
                            .connectionTimeout(Duration.ofSeconds(15)));

            if (config.getEndpoint() != null && !config.getEndpoint().isBlank()) {
                builder.endpointOverride(URI.create(config.getEndpoint()));
            }

            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(config.isPathStyleAccess())
                    .build());

            return builder.build();
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to initialize S3 client for storage %s: %s".formatted(config.getId(), e.getMessage()));
            return null;
        }
    }

    @Override
    public String getId() {
        return id != null ? id : config.getId();
    }

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }

    @Override
    public S3Config getConfig() {
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
        if (s3Client == null) {
            Backuper.getInstance().getLogManager().warn("S3 client is not initialized for storage %s (check credentials and configuration)".formatted(getId()), sender);
            return false;
        }
        if (config.getBucket() == null || config.getBucket().isBlank()) {
            Backuper.getInstance().getLogManager().warn("Bucket name is not specified for S3 storage %s".formatted(getId()), sender);
            return false;
        }

        try {
            logOperation("checkConnection", "headBucket bucket=" + config.getBucket());
            s3Client.headBucket(HeadBucketRequest.builder().bucket(config.getBucket()).build());
            return true;
        } catch (NoSuchBucketException e) {
            Backuper.getInstance().getLogManager().warn("S3 bucket \"%s\" does not exist for storage %s".formatted(config.getBucket(), getId()), sender);
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                Backuper.getInstance().getLogManager().warn("S3 bucket \"%s\" was not found for storage %s".formatted(config.getBucket(), getId()), sender);
            } else if (e.statusCode() == 403) {
                Backuper.getInstance().getLogManager().warn("Access denied for S3 bucket \"%s\" in storage %s (HTTP 403)".formatted(config.getBucket(), getId()), sender);
            } else {
                Backuper.getInstance().getLogManager().warn("S3 connection check failed for storage %s: HTTP %d - %s".formatted(getId(), e.statusCode(), e.getMessage()), sender);
            }
            return false;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("S3 connection check failed for storage %s: %s".formatted(getId(), e.getMessage()), sender);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        String prefix = toDirectoryPrefix(path);
        logOperation("ls", "prefix=" + prefix);

        List<String> result = new ArrayList<>();
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(config.getBucket())
                    .prefix(prefix)
                    .delimiter("/")
                    .build();

            for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(request)) {
                for (CommonPrefix commonPrefix : page.commonPrefixes()) {
                    String subDir = commonPrefix.prefix();
                    if (subDir.endsWith("/")) {
                        subDir = subDir.substring(0, subDir.length() - 1);
                    }
                    int lastSlash = subDir.lastIndexOf('/');
                    String dirName = lastSlash >= 0 ? subDir.substring(lastSlash + 1) : subDir;
                    if (!dirName.isEmpty() && !result.contains(dirName)) {
                        result.add(dirName);
                    }
                }
                for (S3Object s3Object : page.contents()) {
                    String key = s3Object.key();
                    if (key.equals(prefix) || key.equals(prefix + "/")) {
                        continue;
                    }
                    String relative = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
                    if (relative.startsWith("/")) {
                        relative = relative.substring(1);
                    }
                    if (!relative.isEmpty() && !relative.contains("/") && !result.contains(relative)) {
                        result.add(relative);
                    }
                }
            }
            return result;
        } catch (S3Exception e) {
            throw connectionException("ls for path: " + path, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to list contents of path: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        String key = toKey(path);
        if (key.isEmpty()) {
            return checkConnection();
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(config.getBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return checkDirectoryExists(path);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return checkDirectoryExists(path);
            }
            throw connectionException("exists for path: " + path, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to check if path exists: " + path, e);
        }
    }

    private boolean checkDirectoryExists(String path) {
        String dirPrefix = toDirectoryPrefix(path);
        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(config.getBucket())
                    .prefix(dirPrefix)
                    .maxKeys(1)
                    .build());
            return !response.contents().isEmpty() || !response.commonPrefixes().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        String key = toKey(path);
        if (key.isEmpty() || path.endsWith("/")) {
            return false;
        }

        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(config.getBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw connectionException("isFile for path: " + path, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to check if path is a file: " + path, e);
        }
    }

    @Override
    public boolean isDir(String path) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        if (path == null || path.isEmpty() || path.equals("./") || path.equals("/")) {
            return true;
        }
        if (isFile(path)) {
            return false;
        }
        return exists(path);
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        String prefix = toDirectoryPrefix(path);
        logOperation("getDirByteSize", "prefix=" + prefix);

        long totalSize = 0;
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(config.getBucket())
                    .prefix(prefix)
                    .build();

            for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(request)) {
                for (S3Object s3Object : page.contents()) {
                    totalSize += s3Object.size();
                }
            }
            return totalSize;
        } catch (S3Exception e) {
            throw connectionException("getDirByteSize for path: " + path, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to get directory size for path: " + path, e);
        }
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        ensureConnected();
        String dirPath = resolve(parentDir, newDirName);
        String dirKey = toDirectoryPrefix(dirPath);
        if (dirKey.isEmpty()) {
            return;
        }
        logOperation("createDir", "dirKey=" + dirKey);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(dirKey)
                    .contentLength(0L)
                    .build();
            s3Client.putObject(request, RequestBody.empty());
        } catch (S3Exception e) {
            throw connectionException("createDir for dirKey: " + dirKey, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to create directory marker: " + dirKey, e);
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener)
            throws StorageLimitException, StorageMethodException, StorageConnectionException {
        ensureConnected();
        String targetPath = resolve(targetParentDir, newFileName);
        String targetKey = toKey(targetPath);
        logOperation("uploadFile", "targetKey=" + targetKey + " bufferUploadsToDisk=" + config.isBufferUploadsToDisk());

        if (config.isBufferUploadsToDisk()) {
            uploadBufferedToDisk(sourceStream, targetKey, progressListener);
        } else {
            uploadStreaming(sourceStream, targetKey, progressListener);
        }

        // Verify resulting state per requirements
        if (!exists(targetPath)) {
            throw new StorageMethodException(this, "Upload verification failed. Target object does not exist: " + targetPath);
        }
    }

    private void uploadBufferedToDisk(InputStream sourceStream, String targetKey, StorageProgressListener progressListener) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("backuper-s3-", ".upload");
            Files.copy(sourceStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            long fileLength = Files.size(tempFile);
            int partSizeBytes = config.getPartSizeMb() * 1024 * 1024;

            if (fileLength <= partSizeBytes) {
                // Single PutObject
                PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                        .bucket(config.getBucket())
                        .key(targetKey)
                        .contentLength(fileLength);
                if (config.getStorageClass() != null && !config.getStorageClass().isBlank()) {
                    putBuilder.storageClass(config.getStorageClass());
                }

                try (InputStream fileIn = Files.newInputStream(tempFile);
                     StorageProgressInputStream progressIn = new StorageProgressInputStream(fileIn, progressListener)) {
                    s3Client.putObject(putBuilder.build(), RequestBody.fromInputStream(progressIn, fileLength));
                }
            } else {
                // Multipart Upload from file
                uploadMultipartFromFile(tempFile, targetKey, fileLength, partSizeBytes, progressListener);
            }
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to upload file to S3: " + targetKey, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
        }
    }

    private void uploadMultipartFromFile(Path file, String key, long fileLength, int partSizeBytes, StorageProgressListener progressListener) throws IOException {
        CreateMultipartUploadRequest.Builder mpuBuilder = CreateMultipartUploadRequest.builder()
                .bucket(config.getBucket())
                .key(key);
        if (config.getStorageClass() != null && !config.getStorageClass().isBlank()) {
            mpuBuilder.storageClass(config.getStorageClass());
        }

        CreateMultipartUploadResponse mpuResponse = s3Client.createMultipartUpload(mpuBuilder.build());
        String uploadId = mpuResponse.uploadId();
        List<CompletedPart> completedParts = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
             StorageProgressInputStream progressIn = new StorageProgressInputStream(in, progressListener)) {

            byte[] buffer = new byte[partSizeBytes];
            int partNumber = 1;
            int bytesRead;

            while ((bytesRead = progressIn.readNBytes(buffer, 0, buffer.length)) > 0) {
                UploadPartRequest partRequest = UploadPartRequest.builder()
                        .bucket(config.getBucket())
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) bytesRead)
                        .build();

                UploadPartResponse partResponse = s3Client.uploadPart(partRequest, RequestBody.fromBytes(bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead)));
                completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(partResponse.eTag()).build());
                partNumber++;
            }

            CompleteMultipartUploadRequest compRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build();
            s3Client.completeMultipartUpload(compRequest);
        } catch (Exception e) {
            try {
                s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                        .bucket(config.getBucket())
                        .key(key)
                        .uploadId(uploadId)
                        .build());
            } catch (Exception ignored) {}
            throw new StorageMethodException(this, "Failed multipart upload for " + key, e);
        }
    }

    private void uploadStreaming(InputStream sourceStream, String targetKey, StorageProgressListener progressListener) {
        int partSizeBytes = config.getPartSizeMb() * 1024 * 1024;
        try {
            ByteArrayOutputStream firstChunk = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (firstChunk.size() < partSizeBytes) {
                int toRead = Math.min(buffer.length, partSizeBytes - firstChunk.size());
                int read = sourceStream.read(buffer, 0, toRead);
                if (read == -1) {
                    break;
                }
                firstChunk.write(buffer, 0, read);
                progressListener.incrementProgress(read);
            }

            byte[] firstChunkBytes = firstChunk.toByteArray();

            // Try to read one byte to see if EOF has been reached
            int nextByte = sourceStream.read();

            if (nextByte == -1) {
                // Entire payload fits in single PutObject
                PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                        .bucket(config.getBucket())
                        .key(targetKey)
                        .contentLength((long) firstChunkBytes.length);
                if (config.getStorageClass() != null && !config.getStorageClass().isBlank()) {
                    putBuilder.storageClass(config.getStorageClass());
                }
                s3Client.putObject(putBuilder.build(), RequestBody.fromBytes(firstChunkBytes));
                return;
            }

            // More data exists: start multipart upload
            progressListener.incrementProgress(1);
            CreateMultipartUploadRequest.Builder mpuBuilder = CreateMultipartUploadRequest.builder()
                    .bucket(config.getBucket())
                    .key(targetKey);
            if (config.getStorageClass() != null && !config.getStorageClass().isBlank()) {
                mpuBuilder.storageClass(config.getStorageClass());
            }

            CreateMultipartUploadResponse mpuResponse = s3Client.createMultipartUpload(mpuBuilder.build());
            String uploadId = mpuResponse.uploadId();
            List<CompletedPart> completedParts = new ArrayList<>();

            try {
                // Upload part 1
                UploadPartRequest part1Request = UploadPartRequest.builder()
                        .bucket(config.getBucket())
                        .key(targetKey)
                        .uploadId(uploadId)
                        .partNumber(1)
                        .contentLength((long) firstChunkBytes.length)
                        .build();
                UploadPartResponse part1Response = s3Client.uploadPart(part1Request, RequestBody.fromBytes(firstChunkBytes));
                completedParts.add(CompletedPart.builder().partNumber(1).eTag(part1Response.eTag()).build());

                int partNumber = 2;
                ByteArrayOutputStream chunk = new ByteArrayOutputStream();
                chunk.write(nextByte);

                while (true) {
                    while (chunk.size() < partSizeBytes) {
                        int toRead = Math.min(buffer.length, partSizeBytes - chunk.size());
                        int read = sourceStream.read(buffer, 0, toRead);
                        if (read == -1) {
                            break;
                        }
                        chunk.write(buffer, 0, read);
                        progressListener.incrementProgress(read);
                    }

                    if (chunk.size() == 0) {
                        break;
                    }

                    byte[] chunkBytes = chunk.toByteArray();
                    UploadPartRequest partRequest = UploadPartRequest.builder()
                            .bucket(config.getBucket())
                            .key(targetKey)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength((long) chunkBytes.length)
                            .build();
                    UploadPartResponse partResponse = s3Client.uploadPart(partRequest, RequestBody.fromBytes(chunkBytes));
                    completedParts.add(CompletedPart.builder().partNumber(partNumber).eTag(partResponse.eTag()).build());
                    partNumber++;

                    if (chunkBytes.length < partSizeBytes) {
                        break;
                    }
                    chunk.reset();
                }

                CompleteMultipartUploadRequest compRequest = CompleteMultipartUploadRequest.builder()
                        .bucket(config.getBucket())
                        .key(targetKey)
                        .uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                        .build();
                s3Client.completeMultipartUpload(compRequest);
            } catch (Exception e) {
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(config.getBucket())
                            .key(targetKey)
                            .uploadId(uploadId)
                            .build());
                } catch (Exception ignored) {}
                throw e;
            }
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed streaming upload to S3: " + targetKey, e);
        }
    }

    @Override
    public InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        String key = toKey(sourcePath);
        logOperation("downloadFile", "key=" + key);

        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(request);
            return new StorageProgressInputStream(s3Stream, progressListener);
        } catch (NoSuchKeyException e) {
            throw new StorageMethodException(this, "S3 object does not exist for download: " + key, e);
        } catch (S3Exception e) {
            throw connectionException("downloadFile for key: " + key, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to download S3 object: " + key, e);
        }
    }

    @Override
    public void downloadCompleted() throws StorageMethodException, StorageConnectionException {
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        logOperation("delete", "path=" + path);

        try {
            if (isFile(path)) {
                String key = toKey(path);
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(config.getBucket()).key(key).build());
            } else {
                // Directory: delete all objects under prefix
                String prefix = toDirectoryPrefix(path);
                ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                        .bucket(config.getBucket())
                        .prefix(prefix)
                        .build();

                for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(listReq)) {
                    if (page.contents().isEmpty()) {
                        continue;
                    }
                    List<ObjectIdentifier> objectsToDelete = page.contents().stream()
                            .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                            .toList();

                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(config.getBucket())
                            .delete(Delete.builder().objects(objectsToDelete).build())
                            .build());
                }

                // Also delete exact key marker if exists
                String markerKey = toKey(path);
                if (!markerKey.isEmpty()) {
                    try {
                        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(config.getBucket()).key(markerKey).build());
                    } catch (Exception ignored) {}
                }
            }

            if (exists(path)) {
                throw new StorageMethodException(this, "Delete verification failed. Resource still exists: " + path);
            }
        } catch (S3Exception e) {
            throw connectionException("delete for path: " + path, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to delete path: " + path, e);
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        ensureConnected();
        String targetPath = resolve(getParentPath(path), newFileName);
        logOperation("renameFile", "source=" + path + " target=" + targetPath);

        try {
            if (isFile(path)) {
                String sourceKey = toKey(path);
                String targetKey = toKey(targetPath);

                CopyObjectRequest.Builder copyBuilder = CopyObjectRequest.builder()
                        .sourceBucket(config.getBucket())
                        .sourceKey(sourceKey)
                        .destinationBucket(config.getBucket())
                        .destinationKey(targetKey);
                if (config.getStorageClass() != null && !config.getStorageClass().isBlank()) {
                    copyBuilder.storageClass(config.getStorageClass());
                }
                s3Client.copyObject(copyBuilder.build());

                if (!isFile(targetPath)) {
                    throw new StorageMethodException(this, "Rename verification failed. Copied object does not exist: " + targetPath);
                }

                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(config.getBucket()).key(sourceKey).build());
                if (exists(path)) {
                    throw new StorageMethodException(this, "Rename verification failed. Source object still exists: " + path);
                }
            } else {
                // Directory: copy all objects under source prefix to target prefix
                String sourcePrefix = toDirectoryPrefix(path);
                String targetPrefix = toDirectoryPrefix(targetPath);

                ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                        .bucket(config.getBucket())
                        .prefix(sourcePrefix)
                        .build();

                List<ObjectIdentifier> sourceObjectsToDelete = new ArrayList<>();

                for (ListObjectsV2Response page : s3Client.listObjectsV2Paginator(listReq)) {
                    for (S3Object s3Object : page.contents()) {
                        String sourceKey = s3Object.key();
                        String relativePath = sourceKey.startsWith(sourcePrefix) ? sourceKey.substring(sourcePrefix.length()) : sourceKey;
                        String destinationKey = targetPrefix + relativePath;

                        CopyObjectRequest.Builder copyBuilder = CopyObjectRequest.builder()
                                .sourceBucket(config.getBucket())
                                .sourceKey(sourceKey)
                                .destinationBucket(config.getBucket())
                                .destinationKey(destinationKey);
                        if (config.getStorageClass() != null && !config.getStorageClass().isBlank()) {
                            copyBuilder.storageClass(config.getStorageClass());
                        }
                        s3Client.copyObject(copyBuilder.build());
                        sourceObjectsToDelete.add(ObjectIdentifier.builder().key(sourceKey).build());
                    }
                }

                // Delete source objects
                if (!sourceObjectsToDelete.isEmpty()) {
                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(config.getBucket())
                            .delete(Delete.builder().objects(sourceObjectsToDelete).build())
                            .build());
                }

                if (!exists(targetPath)) {
                    throw new StorageMethodException(this, "Rename verification failed. Target directory does not exist: " + targetPath);
                }
                if (exists(path)) {
                    throw new StorageMethodException(this, "Rename verification failed. Source directory still exists: " + path);
                }
            }
        } catch (S3Exception e) {
            throw connectionException("renameFile from " + path + " to " + targetPath, e);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to rename file from " + path + " to " + targetPath, e);
        }
    }

    @Override
    public int getStorageSpeedMultiplier() {
        return 8;
    }

    @Override
    public void destroy() {
        if (protocolLogger != null) {
            protocolLogger.close();
        }
        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (Exception ignored) {}
        }
    }

    private void ensureConnected() throws StorageConnectionException {
        if (s3Client == null) {
            throw new StorageConnectionException(this, "S3 client is not initialized for storage: " + getId());
        }
    }

    private String toKey(String path) {
        if (path == null) return "";
        String normalized = path.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String toDirectoryPrefix(String path) {
        String key = toKey(path);
        if (!key.isEmpty() && !key.endsWith("/")) {
            key = key + "/";
        }
        return key;
    }

    private void logOperation(String operation, String message) {
        if (protocolLogger != null) {
            protocolLogger.logOperation(operation, message);
        }
    }

    private StorageConnectionException connectionException(String action, S3Exception e) {
        return new StorageConnectionException(this, "S3 connection/request error during %s (HTTP %d): %s".formatted(action, e.statusCode(), e.getMessage()), e);
    }
}
