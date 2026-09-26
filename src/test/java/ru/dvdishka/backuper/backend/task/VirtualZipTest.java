package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.LogManager;
import ru.dvdishka.backuper.backend.ScheduleManager;
import ru.dvdishka.backuper.backend.config.StorageConfig;
import ru.dvdishka.backuper.backend.storage.Storage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VirtualZipTest {
    private ScheduleManager executor;
    private Backuper previous;
    private TaskManager tasks;
    private Storage source;
    private Storage target;
    private byte[] data;

    @BeforeEach
    void setup() throws Exception {
        executor = new ScheduleManager();
        previous = Backuper.getInstance();
        Backuper plugin = mock(Backuper.class);
        var field = Backuper.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
        when(plugin.getScheduleManager()).thenReturn(executor);
        when(plugin.getLogManager()).thenReturn(mock(LogManager.class));
        executor.init();
        tasks = new TaskManager();
        when(plugin.getTaskManager()).thenReturn(tasks);
        source = mock(Storage.class);
        target = mock(Storage.class);
        StorageConfig config = mock(StorageConfig.class);
        when(target.getConfig()).thenReturn(config);
        when(config.getZipCompressionLevel()).thenReturn(6);
        data = new byte[6 * 1024 * 1024]; // Incompressible and larger than the pipe plus output buffer.
        new Random(42).nextBytes(data);
        when(source.exists("world.bin")).thenReturn(true);
        when(source.isFile("world.bin")).thenReturn(true);
        when(source.getFileNameFromPath("world.bin")).thenReturn("world.bin");
        when(source.getDirByteSize("world.bin")).thenReturn((long) data.length);
        when(source.downloadFile(eq("world.bin"), any())).thenAnswer(call -> new ByteArrayInputStream(data));
    }

    @AfterEach
    void teardown() throws Exception {
        assertTrue(executor.destroy(Backuper.getInstance()), "Leaked ZIP task");
        var field = Backuper.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, previous);
    }

    private TransferDirsAsZipTask zip() {
        return new TransferDirsAsZipTask(source, List.of("world.bin"), target, "backups", "test.zip", true, true);
    }

    private CompletableFuture<Void> start(TransferDirsAsZipTask task) {
        return executor.runAsync(() -> {
            try { tasks.startTaskRaw(task, null); }
            catch (TaskException e) { throw new CompletionException(e); }
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelledPreparationOrRunFinishesWithoutErrorOrCompletionLogs(boolean cancelDuringPreparation) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var log = Backuper.getInstance().getLogManager();
        var task = new BaseTask() {
            public void prepareTask(CommandSender sender) {
                if (cancelDuringPreparation) waitForCancellation();
            }
            public void run() {
                if (!cancelDuringPreparation) waitForCancellation();
            }
            private void waitForCancellation() {
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    warn("Operation interrupted");
                    warn("Operation interrupted", sender);
                    warn(e);
                    warn(new TaskException(this, e));
                    devWarn("Operation interrupted");
                    devWarn(e);
                    Thread.currentThread().interrupt();
                    throw new CompletionException(e);
                }
            }
        };
        try {
            tasks.startTaskAsync(task, mock(ConsoleCommandSender.class), List.of());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            clearInvocations(log);
            tasks.cancelTaskRaw(task);
            task.getTaskFuture().get(5, TimeUnit.SECONDS);
            assertFalse(task.getTaskFuture().isCompletedExceptionally());
            assertFalse(tasks.isLocked());
            verifyNoInteractions(log);
        } finally {
            tasks.cancelTaskRaw(task);
            release.countDown();
        }
    }

    @Test
    void synchronousCancellationReturnsCancelledInsteadOfCompleted() {
        var task = new BaseTask() {
            public void prepareTask(CommandSender sender) { }
            public void run() {
                cancel();
                throw new CancellationException("Cancelled");
            }
        };
        assertEquals(TaskManager.Result.CANCELLED,
                tasks.startTask(task, mock(ConsoleCommandSender.class), List.of()));
        assertFalse(task.getTaskFuture().isCompletedExceptionally());
        verify(Backuper.getInstance().getLogManager(), never()).warn(any(Exception.class));
    }

    @Test
    void interruptionWithoutCancellationIsStillReportedAsFailure() {
        var failure = new InterruptedIOException("Unexpected I/O interruption");
        var task = new BaseTask() {
            public void prepareTask(CommandSender sender) { }
            public void run() {
                warn(failure);
                throw new CompletionException(failure);
            }
        };
        assertEquals(TaskManager.Result.FAILED,
                tasks.startTask(task, mock(ConsoleCommandSender.class), List.of()));
        assertTrue(task.getTaskFuture().isCompletedExceptionally());
        verify(Backuper.getInstance().getLogManager()).warn(failure);
    }

    @Test
    void multipleArchivesHaveEveryByteAndNoLeakedWriters() throws Exception {
        Queue<byte[]> archives = new ConcurrentLinkedQueue<>();
        doAnswer(call -> {
            archives.add(((InputStream) call.getArgument(0)).readAllBytes());
            return null;
        }).when(target).uploadFile(any(), anyString(), anyString());
        var parent = executor.runAsync(() -> CompletableFuture.allOf(start(zip()), start(zip()), start(zip())).join());
        parent.get(20, TimeUnit.SECONDS);
        assertEquals(3, archives.size());
        for (byte[] archive : archives) {
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                assertEquals("world.bin", zip.getNextEntry().getName());
                assertArrayEquals(data, zip.readAllBytes());
                assertNull(zip.getNextEntry());
            }
            Path file = Files.createTempFile(Path.of("."), "verified-zip-", ".zip");
            try {
                Files.write(file, archive);
                try (var zip = new java.util.zip.ZipFile(file.toFile())) {
                    assertEquals(1, zip.size());
                    assertEquals(data.length, zip.getEntry("world.bin").getSize());
                    try (var input = zip.getInputStream(zip.getEntry("world.bin"))) {
                        assertArrayEquals(data, input.readAllBytes());
                    }
                }
            } finally {
                Files.deleteIfExists(file);
            }
        }
    }

    @Test
    void producerFailureIsNotReportedAsSuccessfulUpload() throws Exception {
        when(source.exists("world.bin")).thenThrow(new IllegalStateException("source failed"));
        doAnswer(call -> { ((InputStream) call.getArgument(0)).readAllBytes(); return null; })
                .when(target).uploadFile(any(), anyString(), anyString());
        var task = zip();
        assertThrows(ExecutionException.class, () -> start(task).get(5, TimeUnit.SECONDS));
        assertFalse(task.isCancelled(), "Failure is not a user cancellation");
    }

    @Test
    void unreadableFileDoesNotPreventFollowingZipEntries() throws Exception {
        when(source.exists("unreadable.bin")).thenReturn(true);
        when(source.isFile("unreadable.bin")).thenReturn(true);
        when(source.getFileNameFromPath("unreadable.bin")).thenReturn("unreadable.bin");
        when(source.downloadFile(eq("unreadable.bin"), any())).thenThrow(new IllegalStateException("file unavailable"));
        var archive = new ByteArrayOutputStream();
        doAnswer(call -> {
            ((InputStream) call.getArgument(0)).transferTo(archive);
            return null;
        }).when(target).uploadFile(any(), anyString(), anyString());
        var task = new TransferDirsAsZipTask(source, List.of("unreadable.bin", "world.bin"),
                target, "backups", "test.zip", true, true);
        start(task).get(5, TimeUnit.SECONDS);
        verify(Backuper.getInstance().getLogManager()).warn("Error adding to ZIP: unreadable.bin", null);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive.toByteArray()))) {
            assertEquals("world.bin", zip.getNextEntry().getName());
            assertArrayEquals(data, zip.readAllBytes());
            assertNull(zip.getNextEntry());
        }
    }

    @Test
    void consumerFailureStopsWriterBlockedByFullPipe() throws Exception {
        CountDownLatch writing = new CountDownLatch(1);
        when(source.downloadFile(eq("world.bin"), any())).thenAnswer(call -> {
            writing.countDown();
            return new ByteArrayInputStream(data);
        });
        doAnswer(call -> {
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            throw new IllegalStateException("upload failed");
        }).when(target).uploadFile(any(), anyString(), anyString());
        assertThrows(ExecutionException.class, () -> start(zip()).get(5, TimeUnit.SECONDS));
    }

    @Test
    void cancellationOfEmptyPipeClosesSourceAndFinishesBothSides() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();
        when(source.downloadFile(eq("world.bin"), any())).thenAnswer(call -> new InputStream() {
            public int read() throws IOException {
                reading.countDown();
                try { new CountDownLatch(1).await(); return -1; }
                catch (InterruptedException e) { throw new InterruptedIOException(); }
            }
            public void close() { closed.set(true); }
        });
        doAnswer(call -> { ((InputStream) call.getArgument(0)).readAllBytes(); return null; })
                .when(target).uploadFile(any(), anyString(), anyString());
        var task = zip();
        var future = start(task);
        assertTrue(reading.await(5, TimeUnit.SECONDS));
        clearInvocations(Backuper.getInstance().getLogManager());
        tasks.cancelTaskRaw(task);
        assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(closed.get());
        verifyNoInteractions(Backuper.getInstance().getLogManager());
    }

    @Test
    void cancellationWithFullPipeFinishesWriterAndConsumer() throws Exception {
        CountDownLatch produced = new CountDownLatch(1);
        when(source.downloadFile(eq("world.bin"), any())).thenAnswer(call -> new ByteArrayInputStream(data) {
            public synchronized int read(byte[] bytes, int offset, int length) {
                int read = super.read(bytes, offset, length);
                if (pos >= 5 * 1024 * 1024) produced.countDown();
                return read;
            }
        });
        doAnswer(call -> {
            // Leave the pipe unread so the producer fills it and must wait for a consumer.
            new CountDownLatch(1).await();
            return null;
        }).when(target).uploadFile(any(), anyString(), anyString());
        var task = zip();
        var future = start(task);
        try {
            assertTrue(produced.await(5, TimeUnit.SECONDS));
            clearInvocations(Backuper.getInstance().getLogManager());
            tasks.cancelTaskRaw(task);
            assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
            verifyNoInteractions(Backuper.getInstance().getLogManager());
        } finally {
            tasks.cancelTaskRaw(task);
        }
    }

    @Test
    void failedWriterPreventsBackupRenameButAllowsPostBackupSteps() throws Exception {
        var configManager = mock(ru.dvdishka.backuper.backend.config.ConfigManager.class);
        var backupConfig = mock(ru.dvdishka.backuper.backend.config.BackupConfig.class);
        when(Backuper.getInstance().getConfigManager()).thenReturn(configManager);
        when(configManager.getBackupConfig()).thenReturn(backupConfig);
        var storageManager = mock(ru.dvdishka.backuper.backend.storage.StorageManager.class);
        when(Backuper.getInstance().getStorageManager()).thenReturn(storageManager);
        when(source.exists("world.bin")).thenThrow(new IllegalStateException("source failed"));
        doAnswer(call -> { ((InputStream) call.getArgument(0)).readAllBytes(); return null; })
                .when(target).uploadFile(any(), anyString(), anyString());
        BackupTask backup = new BackupTask(List.of(target), "NOTHING", true);
        var children = BackupTask.class.getDeclaredField("tasks");
        children.setAccessible(true);
        @SuppressWarnings("unchecked") List<Task> list = (List<Task>) children.get(backup);
        list.add(zip());
        var future = executor.runAsync(backup::run);
        future.get(5, TimeUnit.SECONDS);
        verify(target, never()).renameFile(any(), any());
        verify(configManager).updateLastBackup();
    }

    @Test
    void singleCarrierRegressionRunsInSeparateJvm() throws Exception {
        Path log = Path.of("single-carrier-zip.log").toAbsolutePath();
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-XX:ActiveProcessorCount=1", "-Djdk.virtualThreadScheduler.parallelism=1", "-Djdk.virtualThreadScheduler.maxPoolSize=1",
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                VirtualZipTest.class.getName()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Single-carrier JVM stalled");
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    public static void main(String[] args) throws Exception {
        VirtualZipTest test = new VirtualZipTest();
        test.setup();
        try { test.multipleArchivesHaveEveryByteAndNoLeakedWriters(); }
        finally { test.teardown(); }
        System.out.println("PASS: three complete 6 MiB archives with one carrier");
    }
}
