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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class TaskManager {

    @Getter
    private Task currentTask;
    private List<String> currentTaskPermissions;
    boolean forceLock = false;

    private Result start(Task task, CommandSender sender, List<String> permissions, Function<Runnable, CompletableFuture<Void>> taskExecutor) {
        if (isLocked()) {
            return Result.LOCKED.sendMessage(task, sender);
        }
        if (!hasPermissions(permissions, sender)) {
            return Result.NO_PERMISSION.sendMessage(task, sender);
        }
        currentTask = task;
        currentTaskPermissions = permissions;
        Result.STARTED.sendMessage(task, sender);
        CompletableFuture<Void> taskFuture = taskExecutor.apply(() -> {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(currentTask, sender);
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("An error occurred while executing task %s".formatted(task.getTaskName()));
                Backuper.getInstance().getLogManager().warn(e);
            }
            this.currentTaskPermissions = null;
            this.currentTask = null;
            Result.COMPLETED.sendMessage(task, sender);
        });
        task.setTaskFuture(taskFuture);
        if (taskFuture.isDone()) {
            return Result.COMPLETED;
        } else {
            return Result.STARTED;
        }
    }

    /***
     * Run task using current thread
     */
    public Result startTask(Task task, CommandSender sender, List<String> permissions) {
        return start(task, sender, permissions, (runnable) -> {
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    /***
     * Run task async
     */
    public Result startTaskAsync(Task task, CommandSender sender, List<String> permissions) {
        return start(task, sender, permissions, Backuper.getInstance().getScheduleManager()::runAsync);
    }

    public void startTaskRaw(Task task, CommandSender sender) throws TaskException {
        Backuper.getInstance().getLogManager().devLog("Task %s started".formatted(task.getTaskName()));
        task.start(sender);
        Backuper.getInstance().getLogManager().devLog("Task %s completed".formatted(task.getTaskName()));
    }

    public void cancelTaskRaw(Task task) {
        task.cancel();
        if (task.getPrepareTaskFuture() != null) {
            try {
                task.getPrepareTaskFuture().cancel(false);
                task.getPrepareTaskFuture().join();
            } catch (Exception e) {
                // No need to handle if it was interrupted
            }
        }
        if (task.getTaskFuture() != null) {
            try {
                task.getTaskFuture().cancel(false);
                task.getTaskFuture().join();
            } catch (Exception e) {
                // No need to handle if it was interrupted
            }
        }
    }

    /***
     * Preparation will be completed using a random thread, not current, but this method waits for preparation to be completed
     */
    public void prepareTask(Task task, CommandSender sender) throws Throwable {
        CompletableFuture<Void> prepareTaskFuture = Backuper.getInstance().getScheduleManager().runAsync(() -> {
            Backuper.getInstance().getLogManager().devLog("Preparing task %s".formatted(task.getTaskName()));
            try {
                task.prepareTask(sender);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            } finally {
                Backuper.getInstance().getLogManager().devLog("%s task preparation completed".formatted(task.getTaskName()));
            }
        });
        task.setPrepareTaskFuture(prepareTaskFuture);
        try {
            prepareTaskFuture.get();
        } catch (InterruptedException ignored) {
            // No need to handle if it was interrupted
        } catch (ExecutionException e) {
            throw e.getCause().getCause();
        }
    }

    public Result cancelCurrentTask(CommandSender sender) {
        if (currentTask == null) {
            return Result.NO_TASK_RUNNING.sendMessage(null, sender);
        }
        if (!hasPermissions(currentTaskPermissions, sender)) {
            return Result.NO_PERMISSION.sendMessage(currentTask, sender);
        }
        sendCancellingMessage(sender); // Message that a cancelling process is started. (Cancelling may take a while)
        cancelTaskRaw(currentTask);
        return Result.CANCELLED;
    }

    public boolean isLocked() {
        return currentTask != null && !forceLock;
    }

    private boolean hasPermissions(List<String> permissions, CommandSender sender) {
        return permissions.stream().allMatch(sender::hasPermission);
    }

    public enum Result {
        STARTED(""),
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
            return Component.text(this.message.formatted(task.getTaskName()));
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

    private void sendCancellingMessage(CommandSender sender) {
        Backuper.getInstance().getLogManager().log("Cancelling %s task...".formatted(currentTask.getTaskName()), sender);
    }

    public void forceLock() {
        forceLock = true;
    }

    public void forceUnlock() {
        forceLock = false;
    }
}
