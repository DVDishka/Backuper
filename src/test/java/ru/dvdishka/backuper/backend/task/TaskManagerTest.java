package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.BaseTest;
import ru.dvdishka.backuper.handlers.commands.reload.ReloadCommand;
import ru.dvdishka.backuper.handlers.commands.task.StatusCommand;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerTest extends BaseTest {
    private static class BlockingTask extends BaseTask {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        public void prepareTask(CommandSender sender) { }
        public void run() {
            entered.countDown();
            while (release.getCount() != 0) {
                try { release.await(); } catch (InterruptedException ignored) { }
            }
        }
    }

    @Test
    void simultaneousStartsAdmitOneTaskAndCancellationKeepsSlotUntilExit() throws Exception {
        TaskManager manager = Backuper.getInstance().getTaskManager();
        var scheduler = Backuper.getInstance().getScheduleManager();
        BlockingTask first = new BlockingTask();
        BlockingTask second = new BlockingTask();
        CountDownLatch go = new CountDownLatch(1);
        var results = new ConcurrentLinkedQueue<TaskManager.Result>();
        try {
            var a = scheduler.runAsync(() -> {
                await(go);
                results.add(manager.startTaskAsync(first, server.getConsoleSender(), List.of()));
            });
            var b = scheduler.runAsync(() -> {
                await(go);
                results.add(manager.startTaskAsync(second, server.getConsoleSender(), List.of()));
            });
            go.countDown();
            CompletableFuture.allOf(a, b).get(5, TimeUnit.SECONDS);
            assertEquals(1, results.stream().filter(result -> result == TaskManager.Result.STARTED).count());
            assertEquals(1, results.stream().filter(result -> result == TaskManager.Result.LOCKED).count());
            BlockingTask running = (BlockingTask) manager.getCurrentTask();
            assertTrue(running.entered.await(5, TimeUnit.SECONDS));
            scheduler.runAsync(() -> new StatusCommand(server.getConsoleSender(), null).execute()).get(5, TimeUnit.SECONDS);
            assertEquals(TaskManager.Result.CANCELLED, manager.cancelCurrentTask(server.getConsoleSender()));
            assertTrue(running.isCancelled());
            assertFalse(running.getTaskFuture().isDone());
            assertTrue(manager.isLocked());
            assertFalse(manager.tryLockForReload());
            running.release.countDown();
            running.getTaskFuture().get(5, TimeUnit.SECONDS);
            assertFalse(manager.isLocked());
            assertEquals(TaskManager.Result.NO_TASK_RUNNING, manager.cancelCurrentTask(server.getConsoleSender()));
        } finally {
            first.release.countDown();
            second.release.countDown();
        }
    }

    @Test
    void concurrentBackupLookupSharesOneCachedObject() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."), "backup-cache-");
        java.nio.file.Path backupDirectory = null;
        try {
            config.set("storages.local.backupsFolder", directory.toAbsolutePath().toString());
            reload();
            var plugin = Backuper.getInstance();
            String name = java.time.LocalDateTime.now().format(plugin.getConfigManager().getBackupConfig().getDateTimeFormatter());
            backupDirectory = java.nio.file.Files.createDirectory(directory.resolve(name));
            var manager = plugin.getStorageManager().getStorage("local").getBackupManager();
            var objects = new ConcurrentLinkedQueue<ru.dvdishka.backuper.backend.backup.Backup>();
            CountDownLatch go = new CountDownLatch(1);
            var futures = new java.util.ArrayList<CompletableFuture<Void>>();
            for (int i = 0; i < 40; i++) {
                futures.add(plugin.getScheduleManager().runAsync(() -> {
                    await(go);
                    objects.add(manager.getBackup(name));
                }));
            }
            go.countDown();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
            assertEquals(40, objects.size());
            for (var backup : objects) assertSame(objects.peek(), backup);
        } finally {
            if (backupDirectory != null) java.nio.file.Files.deleteIfExists(backupDirectory);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    @Test
    void forceLockAndRejectedSubmissionCannotLeaveGhostTask() {
        TaskManager manager = Backuper.getInstance().getTaskManager();
        manager.forceLock();
        assertEquals(TaskManager.Result.LOCKED, manager.startTaskAsync(new BlockingTask(), server.getConsoleSender(), List.of()));
        manager.forceUnlock();
        assertTrue(Backuper.getInstance().getScheduleManager().destroy(Backuper.getInstance()));
        assertThrows(RejectedExecutionException.class,
                () -> manager.startTaskAsync(new BlockingTask(), server.getConsoleSender(), List.of()));
        assertNull(manager.getCurrentTask());
    }

    @Test
    void reloadFromExecutorTerminatesOldGeneration() throws Exception {
        config.set("backup.autoBackup", false);
        reload();
        Backuper plugin = Backuper.getInstance();
        var oldScheduler = plugin.getScheduleManager();
        var oldStorages = plugin.getStorageManager();
        oldScheduler.runAsync(() -> {
            // Mockito construction mocks are thread-local; reload constructs metrics on this virtual thread.
            try (var metrics = org.mockito.Mockito.mockConstruction(ru.dvdishka.backuper.backend.Bstats.class)) {
                new ReloadCommand(server.getConsoleSender(), null).execute();
            }
        }).get(5, TimeUnit.SECONDS);
        assertNotSame(oldScheduler, plugin.getScheduleManager());
        assertNotSame(oldStorages, plugin.getStorageManager());
        assertThrows(RejectedExecutionException.class, () -> oldScheduler.runAsync(() -> fail("Old generation ran")));
        plugin.getScheduleManager().runAsync(() -> assertTrue(Thread.currentThread().isVirtual())).get(5, TimeUnit.SECONDS);
        assertFalse(Backuper.restarting);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
    }
}
