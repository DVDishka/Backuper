package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FtpSendFileFolderTask extends Task {

    private static String taskName = "FtpSendFileFolder";

    private File localDirToSend;
    private boolean createRootDirInTargetDir;
    private String remoteTargetDir;
    private boolean forceExcludedDirs;

    private FTPClient ftp;

    public FtpSendFileFolderTask(File localDirToSend, String remoteTargetDir, boolean createRootDirInTargetDir,
                                  boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);

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

            ftp = FtpUtils.createChannel(sender);

            if (ftp == null) {
                return;
            }

            if (createRootDirInTargetDir) {
                remoteTargetDir = FtpUtils.resolve(remoteTargetDir, localDirToSend.getName());
            }

            sendFolder(localDirToSend, remoteTargetDir);

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
            Logger.getLogger().warn(this, e);

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception ignored) {}

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

    private void sendFolder(File localDirToSend, String remotePath) {

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

            try (InputStream inputStream = new FileInputStream(localDirToSend)) {

                if (!ftp.storeFile(remotePath, inputStream)) {
                    throw new IOException("Failed to send file \"" + localDirToSend.getCanonicalPath() + "\" to \"" + remotePath + "\"");
                }
                incrementCurrentProgress(localDirToSend.length());

            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while sending file \"" + localDirToSend.getPath() + "\" to FTP(S) server", e);
                Logger.getLogger().warn(this, e);
            }
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            try {
                ftp.makeDirectory(remotePath);
            } catch (Exception ignored) {}

            for (File file : localDirToSend.listFiles()) {
                sendFolder(file, FtpUtils.resolve(remotePath, file.getName()));
            }
        }
    }
}
