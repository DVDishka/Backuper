package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public interface Task {

    String getTaskName();

    long getTaskPercentProgress();

    long getTaskCurrentProgress();

    long getTaskMaxProgress();

    void incrementCurrentProgress(long progress);

    boolean isCancelled();

    /***
     * This method should only be used to declare Task's run logic, don't use it to start any task. Use TaskManager.startTaskRaw instead
     */
    void run() throws IOException, JSchException, SftpException;

    /***
     * This method should only be used to declare Task's prepare logic, don't use it to prepare any task. Use TaskManager.prepareTask instead
     */
    void prepareTask(CommandSender sender) throws Throwable;

    /***
     * Don't use this method to start any task. Use TaskManager.startTaskRaw instead
     */
    void start(CommandSender sender) throws TaskException;

    /***
     * This method should only be used to declare Task's cancel logic, don't use it to cancel any task. Use TaskManager.cancelCurrentTask instead
     */
    void cancel();

    void setPrepareTaskFuture(CompletableFuture<Void> future);

    CompletableFuture<Void> getPrepareTaskFuture();

    void setTaskFuture(CompletableFuture<Void> future);

    CompletableFuture<Void> getTaskFuture();

    boolean isTaskPrepared();
}
