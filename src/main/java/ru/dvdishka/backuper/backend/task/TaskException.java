package ru.dvdishka.backuper.backend.task;

import lombok.Getter;

public class TaskException extends Exception {

    @Getter
    private final Task task;
    @Getter
    private final Throwable exception;

    public TaskException(Task task, Throwable exception) {
        super("An error occurred while executing task %s".formatted(task.getTaskName()));
        this.task = task;
        this.exception = exception;
        this.setStackTrace(exception.getStackTrace());
    }
}
