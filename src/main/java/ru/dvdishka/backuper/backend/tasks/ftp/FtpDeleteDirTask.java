package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.ArrayList;
import java.util.List;

public class FtpDeleteDirTask extends Task {

    private static final String taskName = "FtpDeleteDir";

    private String remoteDirToDelete = "";

    private FTPClient ftp;

    public FtpDeleteDirTask(String remoteDirToDelete, boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, setLocked, permission, sender);

        this.remoteDirToDelete = remoteDirToDelete;
    }

    @Override
    public void run() {

        try {

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (setLocked) {
                Backuper.lock(this);
            }

            Logger.getLogger().devLog("FtpDeleteDir task started");

            if (!cancelled) {
                ftp = FtpUtils.getClient(sender);
                if (ftp == null) {
                    return;
                }
            }

            if (!cancelled) {
                deleteDir(remoteDirToDelete);
            }

            if (setLocked) {
                Backuper.unlock();
                UIUtils.successSound(sender);
            }

        } catch (Exception e) {
            if (setLocked) {
                Backuper.unlock();
                UIUtils.cancelSound(sender);
            }
            Logger.getLogger().warn("Something went wrong when trying to execute FtpDeleteDir task", sender);
            Logger.getLogger().warn(this, e);
        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn(this, e);
            }

            Logger.getLogger().devLog("FtpDeleteDir task has been finished");
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        maxProgress = FtpUtils.getDirByteSize(remoteDirToDelete, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
        currentProgress = maxProgress;
    }

    private void deleteDir(String remoteDirToDelete) {

        if (cancelled) {
            return;
        }

        try {
            ftp.changeWorkingDirectory("");
            FTPFile remoteFile = ftp.mlistFile(remoteDirToDelete);

            if (remoteFile.isDirectory()) {

                if (!ftp.changeWorkingDirectory(remoteDirToDelete)) {
                    return;
                }

                for (FTPFile file : ftp.listFiles()) {
                    if (file.getName().equals(".") || file.getName().equals("..")) {
                        continue;
                    }
                    deleteDir(FtpUtils.resolve(remoteDirToDelete, file.getName()));
                }
                ftp.changeWorkingDirectory("");
                ftp.removeDirectory(remoteDirToDelete);
            }
            if (remoteFile.isFile()) {
                long fileSize = remoteFile.getSize();
                ftp.deleteFile(remoteDirToDelete);
                incrementCurrentProgress(fileSize);
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Something went while trying to delete FTP(S) directory", sender);
            Logger.getLogger().warn(this, e);
        }
    }
}
