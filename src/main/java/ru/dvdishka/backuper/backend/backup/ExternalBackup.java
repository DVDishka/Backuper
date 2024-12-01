package ru.dvdishka.backuper.backend.backup;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpGetFileFolderTask;
import ru.dvdishka.backuper.backend.tasks.googleDrive.GoogleDriveGetFileFolderTask;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpGetFileFolderTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class ExternalBackup extends Backup {

    private Task getDirectCopyToLocalTask(boolean setLocked, CommandSender sender) {

        String inProgressName = this.getName() + " in progress";
        if (this.getFileType().equals("(ZIP)")) {
            inProgressName += ".zip";
        }
        File inProgressFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder(), inProgressName);

        Task task = null;
        if (this instanceof FtpBackup) {
            task = new FtpGetFileFolderTask(this.getPath(), inProgressFile,
                    false, setLocked, List.of(Permissions.FTP_COPY_TO_LOCAL), sender);
        }
        if (this instanceof SftpBackup) {
            task = new SftpGetFileFolderTask(this.getPath(), inProgressFile,
                    false, setLocked, List.of(Permissions.SFTP_COPY_TO_LOCAL), sender);
        }
        if (this instanceof GoogleDriveBackup) {
            task = new GoogleDriveGetFileFolderTask(this.getPath(), inProgressFile,
                    false, setLocked, List.of(Permissions.GOOGLE_DRIVE_COPY_TO_LOCAL), sender);
        }
        return task;
    }

    public CopyToLocalTask getCopyToLocalTask(boolean setLocked, CommandSender sender) {
        return new CopyToLocalTask(this, setLocked, sender);
    }

    public class CopyToLocalTask extends Task {

        private static final String taskName = "CopyToLocalTask";

        private ExternalBackup backup = null;
        private Task copyToLocalTask = null;

        private static List<Permissions> getPermissions(Backup backup) {
            ArrayList<Permissions> permissions = new ArrayList<>();
            if (backup instanceof FtpBackup) {
                permissions.add(Permissions.FTP_COPY_TO_LOCAL);
            }
            if (backup instanceof SftpBackup) {
                permissions.add(Permissions.SFTP_COPY_TO_LOCAL);
            }
            if (backup instanceof GoogleDriveBackup) {
                permissions.add(Permissions.GOOGLE_DRIVE_COPY_TO_LOCAL);
            }
            return permissions;
        }

        public CopyToLocalTask(ExternalBackup backup, boolean setLocked, List<Permissions> permissions, CommandSender sender) {
            super(taskName, setLocked, permissions, sender);
            this.backup = backup;
        }

        /**
         *When using a constructor without a parameter, the permissions corresponding to the type of backup storage will be taken
         * @param backup
         * @param setLocked
         * @param sender
         */
        public CopyToLocalTask(ExternalBackup backup, boolean setLocked, CommandSender sender) {
            super(taskName, setLocked, getPermissions(backup), sender);
            this.backup = backup;
        }

        @Override
        public void run() {

            if (cancelled) {
                return;
            }

            try {
                if (setLocked) {
                    Backuper.lock(this);
                }

                if (!isTaskPrepared) {
                    prepareTask();
                }

                if (!cancelled) {
                    copyToLocalTask.run();
                }

                if (!cancelled) {

                    String inProgressName = backup.getName() + " in progress";
                    if (backup.getFileType().equals("(ZIP)")) {
                        inProgressName += ".zip";
                    }
                    File inProgressFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder(), inProgressName);
                    final String backupFileName = backup.getFileName();

                    if (!inProgressFile.renameTo(new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName))) {
                        Logger.getLogger().warn("Failed to rename local file: \"" + inProgressFile.getAbsolutePath() + "\" to \"" +
                                new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName).getAbsolutePath() + "\"", sender);
                    }
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

                Logger.getLogger().warn(taskName + " task has been finished with an exception", sender);
                Logger.getLogger().warn(this.getClass(), e);
            }
        }

        @Override
        public void prepareTask() {

            if (cancelled) {
                return;
            }

            copyToLocalTask = backup.getDirectCopyToLocalTask(false, sender);
            copyToLocalTask.prepareTask();

            isTaskPrepared = true;
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (copyToLocalTask != null) {
                copyToLocalTask.cancel();
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return copyToLocalTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return copyToLocalTask.getTaskCurrentProgress();
        }
    }
}
