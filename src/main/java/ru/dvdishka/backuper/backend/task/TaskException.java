package ru.dvdishka.backuper.backend.task;

public class TaskException extends Exception {

    private final Task task;
    private final Exception exception;

    public TaskException(Task task, Exception exception) {
        super("An error occurred while executing task %s".formatted(task.getTaskName()));
        this.task = task;
        this.exception = exception;
    }

    public Exception getException() {
        return this.exception;
    }

    public Task getTask() {
        return this.task;
    }
}
