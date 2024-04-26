package ru.dvdishka.backuper.backend.tasks;

import org.bukkit.command.CommandSender;

public abstract class Task {

    protected CommandSender sender;
    protected String taskName;

    protected long currentProgress = 0;
    protected long maxProgress = 0;
    protected boolean setLocked = false;
    protected boolean isTaskPrepared = false;

    protected Task(String taskName, boolean setLocked, CommandSender sender) {
        this.sender = sender;
        this.setLocked = setLocked;
        this.taskName = taskName;
    }

    public String getTaskName() {
        return this.taskName;
    }

    public long getTaskPercentProgress() {

        if (getTaskMaxProgress() == 0) {
            return 0;
        }
        return (long) (((double) getTaskCurrentProgress()) / ((double) getTaskMaxProgress()) * 100.0);
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

    protected void incrementCurrentProgress(long progress) {
        this.currentProgress += progress;
    }

    abstract public void run();

    abstract public void prepareTask();
}
