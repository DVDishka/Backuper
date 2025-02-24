package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FtpSendFileFolderTask extends Task {

    private static final String taskName = "FtpSendFileFolder";

    private final File localDirToSend;
    private final boolean createRootDirInTargetDir;
    private String remoteTargetDir;
    private final boolean forceExcludedDirs;
    private final ArrayList<CompletableFuture<Void>> ftpTasks = new ArrayList<>();

    private FTPClient ftp;

    public FtpSendFileFolderTask(File localDirToSend, String remoteTargetDir, boolean createRootDirInTargetDir,
                                 boolean forceExcludedDirs, boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, setLocked, permission, sender);

        this.localDirToSend = localDirToSend;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.remoteTargetDir = remoteTargetDir;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() {

        try {

            if (setLocked) {
                Backuper.lock(this);
            }

            if (!isTaskPrepared) {
                prepareTask();
            }

            Logger.getLogger().devLog("FtpSendFileFolderTask has been started");

            if (!cancelled) {
                ftp = FtpUtils.getClient(sender);
                if (ftp == null) {
                    return;
                }
            }

            if (createRootDirInTargetDir) {
                remoteTargetDir = FtpUtils.resolve(remoteTargetDir, localDirToSend.getName());
            }

            if (!cancelled) {
                sendFolder(localDirToSend, remoteTargetDir);
            }

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong when trying to send file/folder from FTP(S) server", sender);
            Logger.getLogger().warn(this.getClass(), e);

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn(this.getClass(), e);
            }

            Logger.getLogger().devLog("FtpSendFileFolder task has been finished");
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        if (forceExcludedDirs) {
            maxProgress = Utils.getFileFolderByteSize(localDirToSend);
        } else {
            maxProgress = Utils.getFileFolderByteSizeExceptExcluded(localDirToSend);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : ftpTasks) {
            task.cancel(true);
        }
    }

    private void sendFolder(File localDirToSend, String remotePath) {

        if (cancelled) {
            return;
        }

        if (!localDirToSend.exists()) {
            Logger.getLogger().warn("Something went wrong while trying to send files from " + localDirToSend.getAbsolutePath());
            Logger.getLogger().warn("Directory " + localDirToSend.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(localDirToSend, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (localDirToSend.isFile() && !localDirToSend.getName().equals("session.lock")) {

            CompletableFuture<Void> ftpTask = CompletableFuture.runAsync(() -> {
                try (InputStream inputStream = new FileInputStream(localDirToSend)) {

                    if (!ftp.storeFile(remotePath, inputStream)) {
                        throw new IOException("Failed to send file \"" + localDirToSend.getCanonicalPath() + "\" to \"" + remotePath + "\"");
                    }
                    incrementCurrentProgress(localDirToSend.length());

                } catch (Exception e) {
                    Logger.getLogger().devWarn(this.getClass(), "Something went wrong while sending file \"" + localDirToSend.getPath() + "\" to FTP(S) server");
                    Logger.getLogger().devWarn(this.getClass(), Arrays.toString(e.getStackTrace()));
                }
            });
            ftpTasks.add(ftpTask);
            try {
                ftpTask.join();
            } catch (Exception e) {
                if (!cancelled) {
                    Logger.getLogger().warn("Something went wrong while sending file \"" + localDirToSend.getPath() + "\" to FTP(S) server", sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }
            }
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            try {
                ftp.makeDirectory(remotePath);
            } catch (Exception ignored) {
            }

            for (File file : localDirToSend.listFiles()) {
                sendFolder(file, FtpUtils.resolve(remotePath, file.getName()));
            }
        }
    }
}
