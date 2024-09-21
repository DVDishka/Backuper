package ru.dvdishka.backuper.backend.tasks.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.SftpProgressMonitor;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SftpSendFileFolderTask extends Task {

    private static final String taskName = "SftpSendFileFolder";

    private File localDirToSend;
    private boolean createRootDirInTargetDir;
    private String remoteTargetDir = "";
    private boolean forceExcludedDirs = false;

    private com.jcraft.jsch.Session sshSession;
    private ChannelSftp sftpChannel;
    private ArrayList<CompletableFuture<Void>> sftpTasks = new ArrayList<>();
    private ArrayList<SftpProgressMonitor> progressMonitors = null;
    private long dirSize = 0;

    public SftpSendFileFolderTask(File localDirToSend, String remoteTargetDir, boolean createRootDirInTargetDir,
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

            Logger.getLogger().devLog("SftpSendFileFolder task has been started");

            if (!cancelled) {
                Pair<Session, ChannelSftp> sessionChannelSftpPair = SftpUtils.createChannel(sender);

                if (sessionChannelSftpPair == null) {
                    return;
                }

                sshSession = sessionChannelSftpPair.getLeft();
                sftpChannel = sessionChannelSftpPair.getRight();

                if (sftpChannel == null) {
                    return;
                }
                sftpChannel.connect(10000);
            }

            if (createRootDirInTargetDir) {
                remoteTargetDir = SftpUtils.resolve(remoteTargetDir, localDirToSend.getName());
            }

            progressMonitors = new ArrayList<>();
            if (!cancelled) {
                sendFolder(localDirToSend, remoteTargetDir);
            }

            sftpChannel.exit();
            sshSession.disconnect();

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (JSchException e) {

            try {
                sftpChannel.disconnect();
            } catch (Exception ignored) {}

            try {
                sshSession.disconnect();
            } catch (Exception ignored) {}

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong when trying to send file/folder through the SFTP channel", sender);
            Logger.getLogger().warn(this, e);
        } finally {
            Logger.getLogger().devLog("SftpSendFileFolder task has been finished");
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        if (forceExcludedDirs) {
            dirSize = Utils.getFileFolderByteSize(localDirToSend);
        } else {
            dirSize = Utils.getFileFolderByteSizeExceptExcluded(localDirToSend);
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

            try {
                String localPath = localDirToSend.getCanonicalPath();

                SftpProgressMonitor progressMonitor = new SftpProgressMonitor();
                progressMonitors.add(progressMonitor);

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    try {
                        sftpChannel.put(localPath, remotePath, progressMonitor);
                    } catch (Exception e) {
                        Logger.getLogger().devWarn(this, "Failed to send file \"" + localPath + "\" using SFTP connection");
                        Logger.getLogger().devWarn(this, Arrays.toString(e.getStackTrace()));
                    }
                });

                sftpTasks.add(task);
                try {
                    task.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        Logger.getLogger().warn("Failed to send file \"" + localPath + "\" using SFTP connection", sender);
                        Logger.getLogger().warn(this, e);
                    }
                }

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while sending file to the SFTP channel", sender);
                Logger.getLogger().warn(this, e);
            }
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            try {
                sftpChannel.mkdir(remotePath);
            } catch (Exception ignored) {}

            for (File file : localDirToSend.listFiles()) {
                 sendFolder(file, SftpUtils.resolve(remotePath, file.getName()));
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return maxProgress;
        }

        if (progressMonitors == null) {
            return 0;
        }
        long currentProgress = 0;

        for (SftpProgressMonitor progressMonitor : progressMonitors) {
            currentProgress += progressMonitor.getCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    @Override
    public void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> sftpTask : sftpTasks) {
            sftpTask.cancel(false);
        }

        currentProgress = maxProgress;
    }
}
