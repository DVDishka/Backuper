package ru.dvdishka.backuper.backend.task;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.UIUtils;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

public class TaskManager {

    @Getter
    private volatile Task currentTask;
    private List<String> currentTaskPermissions;
    private boolean forceLock = false;
    // Preparation may nest inside startTaskRaw on the same thread.
    private final Map<Task, Map<Thread, Integer>> runningThreads = new IdentityHashMap<>();

    void registerCurrentThread(Task task) {
        synchronized (runningThreads) {
            if (task.isCancelled()) throw new CancellationException("Task cancelled");
            runningThreads.computeIfAbsent(task, ignored -> new HashMap<>())
                    .merge(Thread.currentThread(), 1, Integer::sum);
        }
    }

    void unregisterCurrentThread(Task task) {
        synchronized (runningThreads) {
            Map<Thread, Integer> threads = runningThreads.get(task);
            if (threads == null) return;
            threads.computeIfPresent(Thread.currentThread(), (thread, depth) -> depth == 1 ? null : depth - 1);
            if (threads.isEmpty()) runningThreads.remove(task);
        }
    }

    void interruptRunningThreads(Task task) {
        synchronized (runningThreads) {
            Map<Thread, Integer> threads = runningThreads.get(task);
            if (threads == null) return;
            threads.keySet().stream().filter(thread -> thread != Thread.currentThread()).forEach(Thread::interrupt);
        }
    }

    private Result start(Task task, CommandSender sender, List<String> permissions, Function<Runnable, CompletableFuture<Void>> taskExecutor) {
        if (!hasPermissions(permissions, sender)) {
            return Result.NO_PERMISSION.sendMessage(task, sender);
        }
        synchronized (this) {
            if (isLocked()) return Result.LOCKED.sendMessage(task, sender);
            currentTask = task;
            currentTaskPermissions = List.copyOf(permissions);
        }
        try {
            Result.STARTED.sendMessage(task, sender);
            CompletableFuture<Void> taskFuture = taskExecutor.apply(() -> {
                Result outcome = Result.COMPLETED;
                Exception failure = null;
                try {
                    startTaskRaw(task, sender);
                } catch (Exception e) {
                    failure = e;
                    outcome = task.isCancelled() ? Result.CANCELLED : Result.FAILED;
                    if (!task.isCancelled()) {
                        Backuper.getInstance().getLogManager().warn("An error occurred while executing task %s".formatted(task.getTaskName()));
                        Backuper.getInstance().getLogManager().warn(e);
                    }
                } finally {
                    synchronized (this) {
                        currentTaskPermissions = null;
                        currentTask = null;
                    }
                }
                if (!task.isCancelled()) {
                    outcome.sendMessage(task, sender);
                    if (outcome == Result.FAILED) throw new CompletionException(failure);
                }
            });
            task.setTaskFuture(taskFuture);
            return taskFuture.isCompletedExceptionally() ? Result.FAILED :
                    taskFuture.isDone() ? (task.isCancelled() ? Result.CANCELLED : Result.COMPLETED) : Result.STARTED;
        } catch (RuntimeException e) {
            synchronized (this) {
                if (currentTask == task) {
                    currentTask = null;
                    currentTaskPermissions = null;
                }
            }
            throw e;
        }
    }

    /***
     * Run a task using the current thread
     */
    public Result startTask(Task task, CommandSender sender, List<String> permissions) {
        return start(task, sender, permissions, (runnable) -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            try { runnable.run(); future.complete(null); }
            catch (Throwable e) { future.completeExceptionally(e); }
            return future;
        });
    }

    /***
     * Run task async
     */
    public Result startTaskAsync(Task task, CommandSender sender, List<String> permissions) {
        return start(task, sender, permissions, Backuper.getInstance().getScheduleManager()::runAsync);
    }

    public void startTaskRaw(Task task, CommandSender sender) throws TaskException {
        registerCurrentThread(task);
        try {
            if (!task.isCancelled()) Backuper.getInstance().getLogManager().devLog("Task %s started".formatted(task.getTaskName()));
            task.start(sender);
            if (!task.isCancelled()) Backuper.getInstance().getLogManager().devLog("Task %s completed".formatted(task.getTaskName()));
        } finally {
            unregisterCurrentThread(task);
        }
    }

    public void cancelTaskRaw(Task task) {
        try {
            task.cancel();
        } finally {
            interruptRunningThreads(task);
        }
        // Futures describe actual completion, not merely a cancellation request.
    }

    /** Prepare in the calling thread and track its actual completion. */
    public void prepareTask(Task task, CommandSender sender) throws Throwable {
        CompletableFuture<Void> future = new CompletableFuture<>();
        task.setPrepareTaskFuture(future);
        try {
            registerCurrentThread(task);
            try {
                task.prepareTask(sender);
            } finally {
                unregisterCurrentThread(task);
            }
            future.complete(null);
        } catch (Throwable e) {
            future.completeExceptionally(e);
            throw e;
        }
    }

    public Result cancelCurrentTask(CommandSender sender) {
        Task task;
        List<String> permissions;
        synchronized (this) {
            task = currentTask;
            permissions = currentTaskPermissions;
        }
        if (task == null) return Result.NO_TASK_RUNNING.sendMessage(null, sender);
        if (!hasPermissions(permissions, sender)) return Result.NO_PERMISSION.sendMessage(task, sender);
        Backuper.getInstance().getLogManager().log("Cancelling %s task...".formatted(task.getTaskName()), sender);
        cancelTaskRaw(task);
        return Result.CANCELLED;
    }

    public synchronized boolean isLocked() {
        return currentTask != null || forceLock;
    }

    public synchronized boolean tryLockForReload() {
        if (isLocked()) return false;
        forceLock = true;
        return true;
    }

    public void stop() {
        Task task;
        synchronized (this) {
            forceLock = true;
            task = currentTask;
        }
        if (task != null) cancelTaskRaw(task);
    }

    private boolean hasPermissions(List<String> permissions, CommandSender sender) {
        return permissions.stream().allMatch(sender::hasPermission);
    }

    public enum Result {
        STARTED(""),
        FAILED("%s task failed; see the server log"),
        COMPLETED(""),
        CANCELLED("%s task has been successfully cancelled"),
        NO_PERMISSION("You don't have enough permissions"),
        LOCKED("%s task is blocked by another running task"),
        NO_TASK_RUNNING("There are no running tasks");
        
        private final String message;
        
        Result(String message) {
            this.message = message;
        }
        
        private Component getMessage(Task task, CommandSender sender) {
            if (STARTED.equals(this)) {
                return getTaskStartedMessage(task, sender);
            }
            if (COMPLETED.equals(this)) {
                return getTaskCompletedMessage(task, sender);
            }
            return Component.text(this.message.formatted(task == null ? "" : task.getTaskName()));
        }

        /***
         * @return Returns itself
         */
        public Result sendMessage(Task task, CommandSender sender) {
            Backuper.getInstance().getLogManager().log(getMessage(task, sender), sender);
            return this;
        }

        private Component getTaskCompletedMessage(Task task, CommandSender sender) {
            Component message = Component.empty();

            message = message
                    .append(Component.text("The "))
                    .append(Component.text(task.getTaskName())
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(task.isCancelled() ? 0xB02100 : 0x4974B)))
                    .append(Component.text(" task %s".formatted(task.isCancelled() ? "cancelled" : "completed")));

            if (!(sender instanceof ConsoleCommandSender)) {
                return UIUtils.getFramedMessage(message, 15, sender);
            } else {
                return UIUtils.getFramedMessage(message, sender);
            }
        }

        private Component getTaskStartedMessage(Task task, CommandSender sender) {

            Component header = Component.empty();
            Component message = Component.empty();

            if (!(sender instanceof ConsoleCommandSender)) {

                header = header
                        .append(Component.text("The "))
                        .append(Component.text(task.getTaskName())
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.text(" task has been started"));

                message = message
                        .append(Component.text("[STATUS]")
                                .clickEvent(ClickEvent.runCommand("/backuper task status"))
                                .color(TextColor.color(17, 102, 212))
                                .decorate(TextDecoration.BOLD))
                        .append(Component.space())
                        .append(Component.text("[CANCEL]")
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0xB02100))
                                .clickEvent(ClickEvent.runCommand("/backuper task cancel")));
            } else {

                header = header
                        .append(Component.text("The "))
                        .append(Component.text(task.getTaskName())
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.text(" task has been started"));
                message = message
                        .append(Component.text("You can check the task status using command"))
                        .append(Component.newline())
                        .append(Component.text("/backuper task status")
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand("/backuper task status")))
                        .append(Component.newline())
                        .append(Component.text("You can cancel the task using command"))
                        .append(Component.newline())
                        .append(Component.text("/backuper task cancel")
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.suggestCommand("/backuper task cancel")));
            }

            if (!(sender instanceof ConsoleCommandSender)) {
                return UIUtils.getFramedMessage(header, message, 15, sender);
            } else {
                return UIUtils.getFramedMessage(header, message, sender);
            }
        }
    }

    public synchronized void forceLock() {
        forceLock = true;
    }

    public synchronized void forceUnlock() {
        forceLock = false;
    }
}
