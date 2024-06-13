package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;

public class FtpDeleteDirTask extends Task {

    private static final String taskName = "FtpDeleteDir";

    private String remoteDirToDelete = "";

    private FTPClient ftp;

    public FtpDeleteDirTask(String remoteDirToDelete, boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);

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

            ftp = FtpUtils.createChannel(sender);

            if (ftp == null) {
                return;
            }

            deleteDir(remoteDirToDelete);

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
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;

        maxProgress = FtpUtils.getDirByteSize(remoteDirToDelete, sender);
    }

    private void deleteDir(String remoteDirToDelete) {

        try {
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
