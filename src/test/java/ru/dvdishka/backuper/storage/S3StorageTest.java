package ru.dvdishka.backuper.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.dvdishka.backuper.backend.config.S3Config;
import ru.dvdishka.backuper.backend.storage.S3Storage;
import ru.dvdishka.backuper.backend.storage.StorageType;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class S3StorageTest {

    private S3Client s3Client;
    private S3Config config;
    private S3Storage storage;

    @BeforeEach
    public void setUp() {
        s3Client = mock(S3Client.class);
        config = mock(S3Config.class);

        when(config.getId()).thenReturn("s3-test");
        when(config.getBucket()).thenReturn("my-test-bucket");
        when(config.getRegion()).thenReturn("us-east-1");
        when(config.getEndpoint()).thenReturn("");
        when(config.isPathStyleAccess()).thenReturn(false);
        when(config.getBackupsFolder()).thenReturn("backups/");
        when(config.getAccessKeyId()).thenReturn("TESTACCESSKEY");
        when(config.getSecretAccessKey()).thenReturn("TESTSECRETKEY");
        when(config.getSessionToken()).thenReturn("");
        when(config.getPartSizeMb()).thenReturn(5);
        when(config.isBufferUploadsToDisk()).thenReturn(false);
        when(config.getRequestTimeoutSeconds()).thenReturn(3600);
        when(config.isProtocolLogging()).thenReturn(false);
        when(config.getPathSeparatorSymbol()).thenReturn("/");

        storage = new S3Storage(config, s3Client);
    }

    @AfterEach
    public void tearDown() {
        if (storage != null) {
            storage.destroy();
        }
    }

    @Test
    public void testStorageTypeAndProperties() {
        assertEquals(StorageType.S3, storage.getType());
        assertEquals("s3-test", storage.getId());
        assertEquals(8, storage.getStorageSpeedMultiplier());
        assertSame(config, storage.getConfig());
        assertNotNull(storage.getBackupManager());
    }

    @Test
    public void testCheckConnectionSuccess() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        assertTrue(storage.checkConnection());
        verify(s3Client).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    public void testCheckConnectionBucketNotFound() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("Bucket does not exist").build());
        assertFalse(storage.checkConnection());
    }

    @Test
    public void testCheckConnectionAccessDenied() {
        S3Exception accessDenied = (S3Exception) S3Exception.builder().statusCode(403).message("Forbidden").build();
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(accessDenied);
        assertFalse(storage.checkConnection());
    }

    @Test
    public void testExistsAndIsFile() {
        // File exists
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req != null && "backups/test.zip".equals(req.key()))))
                .thenReturn(HeadObjectResponse.builder().contentLength(1024L).build());

        assertTrue(storage.exists("backups/test.zip"));
        assertTrue(storage.isFile("backups/test.zip"));
        assertFalse(storage.isDir("backups/test.zip"));

        // File does not exist, but directory exists
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req != null && "backups/dir".equals(req.key()))))
                .thenThrow(NoSuchKeyException.builder().build());
        when(s3Client.listObjectsV2(argThat((ListObjectsV2Request req) -> req != null && "backups/dir/".equals(req.prefix()))))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("backups/dir/file.txt").build())
                        .build());

        assertTrue(storage.exists("backups/dir"));
        assertFalse(storage.isFile("backups/dir"));
        assertTrue(storage.isDir("backups/dir"));

        // Non-existent path
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req != null && "nonexistent".equals(req.key()))))
                .thenThrow(NoSuchKeyException.builder().build());
        when(s3Client.listObjectsV2(argThat((ListObjectsV2Request req) -> req != null && "nonexistent/".equals(req.prefix()))))
                .thenReturn(ListObjectsV2Response.builder().contents(Collections.emptyList()).build());

        assertFalse(storage.exists("nonexistent"));
        assertFalse(storage.isFile("nonexistent"));
        assertFalse(storage.isDir("nonexistent"));
    }

    @Test
    public void testExistsThrowsOnServerError() {
        S3Exception serverError = (S3Exception) S3Exception.builder().statusCode(500).message("Internal error").build();
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(serverError);

        assertThrows(StorageConnectionException.class, () -> storage.exists("backups/test.zip"));
    }

    @Test
    public void testCreateDir() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storage.createDir("new_folder", "backups");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("backups/new_folder/", captor.getValue().key());
        assertEquals(0L, captor.getValue().contentLength());
    }

    @Test
    public void testLs() {
        ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
        ListObjectsV2Response page = ListObjectsV2Response.builder()
                .commonPrefixes(CommonPrefix.builder().prefix("backups/world_folder/").build())
                .contents(
                        S3Object.builder().key("backups/").build(), // marker
                        S3Object.builder().key("backups/2026-09-14.zip").build(),
                        S3Object.builder().key("backups/another.zip").build()
                )
                .build();

        when(iterable.iterator()).thenReturn(List.of(page).iterator());
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);

        List<String> results = storage.ls("backups");
        assertEquals(3, results.size());
        assertTrue(results.contains("world_folder"));
        assertTrue(results.contains("2026-09-14.zip"));
        assertTrue(results.contains("another.zip"));
    }

    @Test
    public void testGetDirByteSize() {
        ListObjectsV2Iterable iterable = mock(ListObjectsV2Iterable.class);
        ListObjectsV2Response page = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder().key("backups/f1.zip").size(1000L).build(),
                        S3Object.builder().key("backups/f2.zip").size(2000L).build()
                )
                .build();

        when(iterable.iterator()).thenReturn(List.of(page).iterator());
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenReturn(iterable);

        long size = storage.getDirByteSize("backups");
        assertEquals(3000L, size);
    }

    @Test
    public void testUploadSmallFileSinglePut() {
        byte[] content = "Hello S3 Backup!".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(content);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        // For upload verification:
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req.key().equals("backups/test.txt"))))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) content.length).build());

        BasicStorageProgressListener progressListener = new BasicStorageProgressListener();
        storage.uploadFile(in, "test.txt", "backups", progressListener);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("backups/test.txt", captor.getValue().key());
        assertEquals(content.length, captor.getValue().contentLength());
        assertEquals(content.length, progressListener.getCurrentProgress());
    }

    @Test
    public void testUploadBufferedToDisk() {
        when(config.isBufferUploadsToDisk()).thenReturn(true);
        byte[] content = "Buffered upload content".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(content);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req.key().equals("backups/buffered.txt"))))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) content.length).build());

        storage.uploadFile(in, "buffered.txt", "backups", new BasicStorageProgressListener());

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("backups/buffered.txt", captor.getValue().key());
    }

    @Test
    public void testDownloadFile() throws Exception {
        byte[] content = "Downloaded S3 payload".getBytes(StandardCharsets.UTF_8);
        AbortableInputStream abortable = AbortableInputStream.create(new ByteArrayInputStream(content));
        ResponseInputStream<GetObjectResponse> respStream = new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength((long) content.length).build(),
                abortable
        );

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(respStream);

        BasicStorageProgressListener progress = new BasicStorageProgressListener();
        try (InputStream stream = storage.downloadFile("backups/test.zip", progress)) {
            byte[] readContent = stream.readAllBytes();
            assertArrayEquals(content, readContent);
            assertEquals(content.length, progress.getCurrentProgress());
        }
    }

    @Test
    public void testDeleteFile() {
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req != null && "backups/file.zip".equals(req.key()))))
                .thenReturn(HeadObjectResponse.builder().build()) // for isFile
                .thenThrow(NoSuchKeyException.builder().build()); // for exists after delete

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(Collections.emptyList()).build());

        storage.delete("backups/file.zip");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("backups/file.zip", captor.getValue().key());
    }

    @Test
    public void testRenameFile() {
        // Source is a file
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req != null && "backups/in_progress.zip".equals(req.key()))))
                .thenReturn(HeadObjectResponse.builder().build()) // isFile(source)
                .thenThrow(NoSuchKeyException.builder().build()); // exists(source) after delete

        // Target exists after copy
        when(s3Client.headObject(argThat((HeadObjectRequest req) -> req != null && "backups/final.zip".equals(req.key()))))
                .thenReturn(HeadObjectResponse.builder().build()); // isFile(target) verification

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().contents(Collections.emptyList()).build());

        storage.renameFile("backups/in_progress.zip", "final.zip");

        ArgumentCaptor<CopyObjectRequest> copyCaptor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(copyCaptor.capture());
        assertEquals("backups/in_progress.zip", copyCaptor.getValue().sourceKey());
        assertEquals("backups/final.zip", copyCaptor.getValue().destinationKey());

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertEquals("backups/in_progress.zip", deleteCaptor.getValue().key());
    }
}
