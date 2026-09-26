package ru.dvdishka.backuper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.dvdishka.backuper.backend.Bstats;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.task.SetWorldsReadOnlyTask;
import ru.dvdishka.backuper.backend.task.TaskManager;
import ru.dvdishka.backuper.backend.task.TransferDirTask;
import ru.dvdishka.backuper.handlers.commands.reload.ReloadCommand;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackupLifecycleIT extends BaseTest {
    @TempDir Path directory;

    private org.mockbukkit.mockbukkit.world.WorldMock addWorld(String name) throws Exception {
        var world = spy(new org.mockbukkit.mockbukkit.world.WorldMock());
        doReturn(name).when(world).getName();
        doReturn(Files.createDirectory(directory.resolve(name)).toFile()).when(world).getWorldFolder();
        server.addWorld(world);
        return world;
    }

    @Test
    void uploadFailureRestoresWorldAndDoesNotFinalizeBackup() throws Exception {
        var world = addWorld("backup-lifecycle");
        world.setAutoSave(true);
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("data.txt"), "backup data");
        config.set("backup.setWorldsReadOnly", true);
        config.set("backup.skipDuplicateBackup", false);
        config.set("backup.addDirectoryToBackup", List.of(source.toString()));
        config.set("storages.local.backupsFolder", directory.resolve("backups").toString());
        config.set("storages.local.zipArchive", true);
        reload();
        Storage storage = spy(Backuper.getInstance().getStorageManager().getStorage("local"));
        doAnswer(call -> {
            assertFalse(world.isAutoSave());
            ((InputStream) call.getArgument(0)).read();
            throw new StorageMethodException(storage, "Injected upload failure");
        }).when(storage).uploadFile(any(), anyString(), anyString());
        BackupTask backup = new BackupTask(List.of(storage), "NOTHING", true);

        var result = Backuper.getInstance().getTaskManager().startTask(backup, server.getConsoleSender(), List.of());
        assertEquals(TaskManager.Result.COMPLETED, result);
        assertTrue(world.isAutoSave());
        assertFalse(Backuper.getInstance().getTaskManager().isLocked());
        verify(storage, never()).renameFile(anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prepare", "upload", "rename"})
    void failedStorageDoesNotPreventHealthyBackupOrPostBackupSteps(String stage) throws Exception {
        Path source = Files.createDirectory(directory.resolve("source"));
        Files.writeString(source.resolve("data.txt"), "backup data");
        Path backups = directory.resolve("backups");
        config.set("backup.skipDuplicateBackup", false);
        config.set("backup.addDirectoryToBackup", List.of(source.toString()));
        config.set("storages.local.backupsFolder", backups.toString());
        config.set("storages.local.zipArchive", !stage.equals("prepare"));
        config.set("lastBackup", 0);
        reload();
        var plugin = Backuper.getInstance();
        Storage healthy = plugin.getStorageManager().getStorage("local");
        assertEquals(0, plugin.getConfigManager().getLastBackup());
        Storage failed = mock(Storage.class);
        when(failed.getId()).thenReturn("failed");
        when(failed.getConfig()).thenReturn(healthy.getConfig());
        when(failed.resolve(anyString(), anyString())).thenAnswer(call ->
                Path.of((String) call.getArgument(0), (String) call.getArgument(1)).toString());
        when(failed.exists(anyString())).thenReturn(true);
        var failure = new StorageMethodException(failed, "Injected " + stage + " failure");
        if (stage.equals("prepare")) {
            doThrow(failure).when(failed).createDir(anyString(), anyString());
        } else if (stage.equals("upload")) {
            doThrow(failure).when(failed).uploadFile(any(), anyString(), anyString());
        } else {
            doAnswer(call -> {
                ((InputStream) call.getArgument(0)).readAllBytes();
                return null;
            }).when(failed).uploadFile(any(), anyString(), anyString());
            doThrow(failure).when(failed).renameFile(anyString(), anyString());
        }

        var result = plugin.getTaskManager().startTask(
                new BackupTask(List.of(failed, healthy), "NOTHING", true), server.getConsoleSender(), List.of());

        assertEquals(TaskManager.Result.COMPLETED, result);
        assertTrue(plugin.getConfigManager().getLastBackup() > 0, "Post-backup steps must still run");
        try (var files = Files.list(backups)) {
            var completed = files.toList();
            assertEquals(1, completed.size());
            Path backup = completed.getFirst();
            assertFalse(backup.getFileName().toString().contains("in progress"));
            if (stage.equals("prepare")) {
                assertEquals("backup data", Files.readString(backup.resolve("source/data.txt")));
            } else {
                try (var zip = new java.util.zip.ZipFile(backup.toFile());
                     var input = zip.getInputStream(zip.getEntry("source/data.txt"))) {
                    assertEquals("backup data", new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
    }

    @Test
    void failedDirectoryPreparationKeepsEarlierAndLaterPreparedTasks() throws Exception {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path failed = Files.createDirectory(directory.resolve("failed"));
        Path last = Files.createDirectory(directory.resolve("last"));
        config.set("backup.skipDuplicateBackup", false);
        config.set("backup.addDirectoryToBackup", List.of(first.toString(), failed.toString(), last.toString()));
        config.set("storages.local.backupsFolder", directory.resolve("backups").toString());
        config.set("storages.local.zipArchive", false);
        reload();
        var plugin = Backuper.getInstance();
        Storage storage = plugin.getStorageManager().getStorage("local");
        try (var transfers = mockConstruction(TransferDirTask.class, (task, context) -> {
            when(task.getTargetStorage()).thenReturn(storage);
            when(task.getTaskName()).thenReturn("TransferDir");
            if (Path.of((String) context.arguments().get(1)).equals(failed)) {
                doThrow(new IllegalStateException("Injected directory preparation failure"))
                        .when(task).prepareTask(any());
            }
        })) {
            var result = plugin.getTaskManager().startTask(
                    new BackupTask(List.of(storage), "NOTHING", true), server.getConsoleSender(), List.of());
            assertEquals(TaskManager.Result.COMPLETED, result);
            assertEquals(3, transfers.constructed().size());
            verify(transfers.constructed().get(0)).start(any());
            verify(transfers.constructed().get(1), never()).start(any());
            verify(transfers.constructed().get(2)).start(any());
        }
    }

    @Test
    void disableInterruptsWorkAndRestoresWorldWithoutSubmittingPreparation() throws Exception {
        var world = addWorld("disable-lifecycle");
        world.setAutoSave(true);
        config.set("backup.setWorldsReadOnly", true);
        reload();
        new SetWorldsReadOnlyTask().run();
        assertFalse(world.isAutoSave());
        var started = new CountDownLatch(1);
        var completion = Backuper.getInstance().getScheduleManager().runAsync(() -> {
            started.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTimeout(Duration.ofSeconds(5), () -> assertTrue(Backuper.getInstance().shutdown()));
        completion.get(1, TimeUnit.SECONDS);
        assertTrue(world.isAutoSave());
    }

    @Test
    void reloadRestoresWorldInCallingThreadWithoutScheduling() throws Exception {
        var world = addWorld("reload-lifecycle");
        world.setAutoSave(true);
        config.set("backup.setWorldsReadOnly", true);
        config.set("backup.autoBackup", false);
        reload();
        new SetWorldsReadOnlyTask().run();
        assertFalse(world.isAutoSave());
        var plugin = Backuper.getInstance();
        var oldScheduler = plugin.getScheduleManager();
        clearInvocations(oldScheduler);
        oldScheduler.runAsync(() -> {
            Thread caller = Thread.currentThread();
            doAnswer(call -> {
                assertSame(caller, Thread.currentThread());
                return call.callRealMethod();
            }).when(world).setAutoSave(true);
            // Construction mocks are thread-local; reload creates metrics in this thread.
            try (var metrics = mockConstruction(Bstats.class)) {
                new ReloadCommand(server.getConsoleSender(), null).execute();
            }
            assertTrue(world.isAutoSave());
        }).get(5, TimeUnit.SECONDS);
        assertNotSame(oldScheduler, plugin.getScheduleManager());
        verify(oldScheduler, never()).runGlobalRegionDelayed(any(), any(), anyLong());
    }
}
