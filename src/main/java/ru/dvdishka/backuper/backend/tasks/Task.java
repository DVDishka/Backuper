package ru.dvdishka.backuper.backend.tasks;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

import static com.google.common.primitives.Doubles.min;

public abstract class Task {

    protected CommandSender sender;
    protected String taskName;
    protected List<Permissions> permissions;

    protected long currentProgress = 0;
    protected long maxProgress = 0;
    protected boolean setLocked = false;
    protected boolean isTaskPrepared = false;
    protected boolean cancelled = false;

    protected Task(String taskName, boolean setLocked, List<Permissions> permissions, CommandSender sender) {
        this.sender = sender;
        this.setLocked = setLocked;
        this.taskName = taskName;
        this.permissions = permissions;
    }

    public String getTaskName() {
        return this.taskName;
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

    public boolean getSetLocked() {
        return setLocked;
    }

    public List<Permissions> getPermissions() {
        return permissions;
    }

    protected synchronized void incrementCurrentProgress(long progress) {
        this.currentProgress += progress;
    }

    abstract public void run();

    abstract public void prepareTask();

    abstract public void cancel();
}
