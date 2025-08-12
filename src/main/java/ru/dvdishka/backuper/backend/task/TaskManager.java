package ru.dvdishka.backuper.backend.task;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class TaskManager {

    private BaseTask currentTask;
    private List<Permissions> currentTaskPermissions;

    private Result start(BaseAsyncTask task, CommandSender sender, List<Permissions> permissions, Function<Runnable, CompletableFuture<Void>> taskExecutor) {
        if (currentTask != null) {
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
            }
            this.currentTaskPermissions = null;
            this.currentTask = null;
            Result.COMPLETED.sendMessage(task, sender);
        });
        if (taskFuture.isDone()) {
            return Result.COMPLETED;
        } else {
            return Result.STARTED;
        }
    }

    /***
     * Run task using current thread
     */
    public Result startTask(BaseAsyncTask task, CommandSender sender, List<Permissions> permissions) {
        return start(task, sender, permissions, (runnable) -> {
            runnable.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    /***
     * Run task async
     */
    public Result startTaskAsync(BaseAsyncTask task, CommandSender sender, List<Permissions> permissions) {
        return start(task, sender, permissions, Backuper.getInstance().getScheduleManager()::runAsync);
    }

    public void startTaskRaw(BaseTask task, CommandSender sender) throws TaskException {
        task.start(sender);
    }

    public void cancelTaskRaw(BaseTask task) {
        task.cancel();
    }

    /***
     * Preparation will be completed using a random thread, not current, but this method waits for preparation to be completed
     */
    public void prepareTask(BaseTask task, CommandSender sender) throws ExecutionException, InterruptedException {
        CompletableFuture<Void> prepareTaskFuture = Backuper.getInstance().getScheduleManager().runAsync(() -> {
            try {
                task.prepareTask(sender);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        task.setPrepareTaskFuture(prepareTaskFuture);
        prepareTaskFuture.get();
    }

    public Result cancelTask(BaseTask task, CommandSender sender) {
        if (currentTask == null) {
            return Result.NO_TASK_RUNNING.sendMessage(task, sender);
        }
        if (!hasPermissions(currentTaskPermissions, sender)) {
            return Result.NO_PERMISSION.sendMessage(task, sender);
        }
        sendCancellingMessage(sender);// Message that a cancelling process is started. (Cancelling may take a while)
        task.cancel();
        currentTask = null;
        Result.CANCELLED.sendMessage(task, sender);
        startTask(new DeleteBrokenBackupsTask(), sender, currentTaskPermissions);
        return Result.CANCELLED;
    }

    public boolean isLocked() {
        return currentTask != null;
    }

    public BaseTask getCurrentTask() {
        return currentTask;
    }

    private boolean hasPermissions(List<Permissions> permissions, CommandSender sender) {
        return permissions.stream().allMatch((permission) -> sender.hasPermission(permission.getPermission()));
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
        
        private Component getMessage(BaseTask task, CommandSender sender) {
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
        public Result sendMessage(BaseTask task, CommandSender sender) {
            UIUtils.sendMessage(getMessage(task, sender), sender);
            return this;
        }

        private Component getTaskCompletedMessage(BaseTask task, CommandSender sender) {
            Component message = Component.empty();

            message = message
                    .append(Component.text("The "))
                    .append(Component.text(task.getTaskName())
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x4974B)))
                    .append(Component.text(" task completed"));

            if (!(sender instanceof ConsoleCommandSender)) {
                return UIUtils.getFramedMessage(message, 15, sender);
            } else {
                return UIUtils.getFramedMessage(message, sender);
            }
        }

        private Component getTaskStartedMessage(BaseTask task, CommandSender sender) {

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
                                .clickEvent(ClickEvent.runCommand("/backuper task cancelConfirmation")));
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
        UIUtils.sendMessage("Cancelling %s task...".formatted(currentTask.getTaskName()), sender);
    }
}
