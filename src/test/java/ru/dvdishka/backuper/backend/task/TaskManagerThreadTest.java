package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.BaseTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TaskManagerThreadTest extends BaseTest {
    private static class BlockingTask extends BaseTask {
        final boolean blockPreparation;
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean interrupted = new AtomicBoolean();

        BlockingTask(boolean blockPreparation) {
            this.blockPreparation = blockPreparation;
        }

        public void prepareTask(CommandSender sender) {
            if (blockPreparation) awaitCancellation();
        }

        public void run() {
            if (!blockPreparation) awaitCancellation();
        }

        private void awaitCancellation() {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                throw new CancellationException("Interrupted by task cancellation");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void managedStartCanBeCancelledDuringPreparationOrRun(boolean blockPreparation) throws Exception {
        var task = new BlockingTask(blockPreparation);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var completion = executor.submit(() -> {
                    Backuper.getInstance().getTaskManager().startTaskRaw(task, null);
                    return null;
                });
                assertTrue(task.entered.await(5, TimeUnit.SECONDS));
                Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
                assertThrows(ExecutionException.class, () -> completion.get(5, TimeUnit.SECONDS));
                assertTrue(task.interrupted.get());
            } finally {
                Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
                task.release.countDown();
            }
        }
    }

    @Test
    void standalonePreparationCanBeCancelledThroughTaskManager() throws Exception {
        var task = new BlockingTask(true);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var completion = executor.submit(() -> {
                    try {
                        Backuper.getInstance().getTaskManager().prepareTask(task, null);
                    } catch (Throwable e) {
                        throw new CompletionException(e);
                    }
                });
                assertTrue(task.entered.await(5, TimeUnit.SECONDS));
                Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
                assertThrows(ExecutionException.class, () -> completion.get(5, TimeUnit.SECONDS));
                assertTrue(task.interrupted.get());
                assertTrue(task.getPrepareTaskFuture().isCompletedExceptionally());
            } finally {
                Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
                task.release.countDown();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "prepare", "run"})
    void completedOrFailedTaskDoesNotInterruptLaterWorkOnSameThread(String failurePhase) throws Exception {
        var task = new BaseTask() {
            public void prepareTask(CommandSender sender) {
                if (failurePhase.equals("prepare")) throw new IllegalStateException("Preparation failed");
            }
            public void run() {
                if (failurePhase.equals("run")) throw new IllegalStateException("Execution failed");
            }
        };
        var finished = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var completion = executor.submit(() -> {
                    if (failurePhase.equals("none")) Backuper.getInstance().getTaskManager().startTaskRaw(task, null);
                    else assertThrows(TaskException.class, () -> Backuper.getInstance().getTaskManager().startTaskRaw(task, null));
                    finished.countDown();
                    try {
                        release.await();
                        return Thread.currentThread().isInterrupted();
                    } catch (InterruptedException e) {
                        return true;
                    }
                });
                assertTrue(finished.await(5, TimeUnit.SECONDS));
                Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
                release.countDown();
                assertFalse(completion.get(5, TimeUnit.SECONDS), "Task retained a thread after leaving start()");
            } finally {
                release.countDown();
            }
        }
    }
}
