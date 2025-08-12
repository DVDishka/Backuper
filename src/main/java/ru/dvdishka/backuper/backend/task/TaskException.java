package ru.dvdishka.backuper.backend.task;

public class TaskException extends Exception {

  private BaseTask task;
  private Exception exception;

  public TaskException(BaseTask task, Exception exception) {
        super("An error occurred while executing task %s".formatted(task.getTaskName()));
        this.task = task;
        this.exception = exception;
  }

  public Exception getException() {
    return this.exception;
  }

  public BaseTask getTask() {
    return this.task;
  }
}
