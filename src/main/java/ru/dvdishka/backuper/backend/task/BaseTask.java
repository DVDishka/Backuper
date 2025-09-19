package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import ru.dvdishka.backuper.Backuper;

import java.util.concurrent.CompletableFuture;

import static java.lang.Math.min;

public abstract class BaseTask implements Task {

    protected CommandSender sender = null; //Any task must be able to send some information to sender without crash (For example when only one storage is not available and the BackupTask shouldn't be aborted)
    protected String taskName;

    protected long currentProgress = 0;
    protected long maxProgress = 0;
    protected boolean cancelled = false;
    protected CompletableFuture<Void> prepareTaskFuture = null;

    protected BaseTask() {}

    public String getTaskName() {
        return this.getClass().getSimpleName().replace("Task", "");
    }

    public long getTaskPercentProgress() {

        if (getTaskMaxProgress() == 0) {
            return 0;
        }
        return (long) min((((double) getTaskCurrentProgress()) / ((double) getTaskMaxProgress()) * 100.0), 100.0);
    }

    public long getTaskCurrentProgress() {
        return currentProgress;
    }

    public long getTaskMaxProgress() {
        return maxProgress;
    }

    public synchronized void incrementCurrentProgress(long progress) {
        this.currentProgress += progress;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /***
     * This method should only be used to declare Task's run logic, don't use it to start any task. Use TaskManager.startTaskRaw instead
     */
    @ApiStatus.Internal
    public abstract void run();

    /***
     * This method should only be used to declare Task's prepare logic, don't use it to prepare any task. Use TaskManager.prepareTask instead
     */
    @ApiStatus.Internal
    public abstract void prepareTask(CommandSender sender) throws Throwable;

    /***
     * Don't use this method to start any task. Use TaskManager.startTaskRaw instead
     */
    @ApiStatus.Internal
    public void start(CommandSender sender) throws TaskException {
        if (!cancelled) {
            this.sender = sender;
        }
        if (!isTaskPrepared() && !cancelled) {
            try {
                Backuper.getInstance().getTaskManager().prepareTask(this, sender);
            } catch (Throwable e) {
                throw new TaskException(this, e);
            }
        }
        if (!cancelled) {
            try {
                prepareTaskFuture.get();
            } catch (Exception e) {
                throw new TaskException(this, e);
            }
        }
        if (!cancelled) {
            try {
                run();
            } catch (Exception e) {
                throw new TaskException(this, e);
            }
        }
    }

    /***
     * This method should only be used to declare Task's cancel logic, don't use it to cancel any task. Use TaskManager.cancel instead
     */
    @ApiStatus.Internal
    public abstract void cancel();

    @ApiStatus.Internal
    public void setPrepareTaskFuture(CompletableFuture<Void> future) {
        this.prepareTaskFuture = future;
    }

    protected void warn(String message) {
        Backuper.getInstance().getLogManager().warn(message);
    }

    protected void warn(String message, CommandSender sender) {
        Backuper.getInstance().getLogManager().warn(message, sender);
    }

    protected void warn(Exception e) {
        Backuper.getInstance().getLogManager().warn(e);
    }

    protected void warn(TaskException e) {
        Backuper.getInstance().getLogManager().warn(e);
    }

    protected void log(String message) {
        Backuper.getInstance().getLogManager().log(message);
    }

    protected void log(String message, CommandSender sender) {
        Backuper.getInstance().getLogManager().log(message, sender);
    }

    protected void devLog(String message) {
        Backuper.getInstance().getLogManager().devLog(message);
    }

    protected void devWarn(String message) {
        Backuper.getInstance().getLogManager().devWarn(message);
    }

    protected void devWarn(Exception e) {
        Backuper.getInstance().getLogManager().devWarn(e);
    }

    public boolean isTaskPrepared() {
        return prepareTaskFuture != null && prepareTaskFuture.isDone();
    }
}
