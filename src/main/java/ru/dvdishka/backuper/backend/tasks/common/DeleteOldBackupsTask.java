package ru.dvdishka.backuper.backend.tasks.common;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class DeleteOldBackupsTask extends Task {

    private static String taskName = "DeleteOldBackups";

    private ArrayList<Task> deleteBackupTasks = new ArrayList<>();

    public DeleteOldBackupsTask(boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, setLocked, permission, sender);
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {
            Logger.getLogger().devLog("DeleteOldBackups task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            for (Task deleteDirTask : deleteBackupTasks) {
                if (!cancelled) {
                    deleteDirTask.run();
                }
            }

            Logger.getLogger().devLog("DeleteOldBackups task has been finished");

            if (setLocked) {

                Logger.getLogger().log("DeleteOldBackups task completed", sender);
                UIUtils.successSound(sender);
                Backuper.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running DeleteOldBackups task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    @Override
    public void prepareTask() {

        isTaskPrepared = true;

        try {

            if (!cancelled && Config.getInstance().getLocalConfig().isEnabled()) {
                deleteBackups("local");
            }
            if (!cancelled && Config.getInstance().getFtpConfig().isEnabled()) {
                deleteBackups("ftp");
            }
            if (!cancelled && Config.getInstance().getSftpConfig().isEnabled()) {
                deleteBackups("sftp");
            }
            if (!cancelled && Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.isAuthorized(null)) {
                deleteBackups("googleDrive");
            }

        } catch (Exception e) {

            Logger.getLogger().warn("DeleteOldBackupsTask failed", sender);
            Logger.getLogger().warn(DeleteOldBackupsTask.class, e);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;

        for (Task deleteDirTask : deleteBackupTasks) {
            deleteDirTask.cancel();
        }
    }

    private void deleteBackups(String storage) {

        if (cancelled ||
                storage.equals("local") && Config.getInstance().getLocalConfig().getBackupsNumber() == 0 && Config.getInstance().getLocalConfig().getBackupsWeight() == 0 ||
                storage.equals("ftp") && Config.getInstance().getFtpConfig().getBackupsNumber() == 0 && Config.getInstance().getFtpConfig().getBackupsWeight() == 0 ||
                storage.equals("sftp") && Config.getInstance().getSftpConfig().getBackupsNumber() == 0 && Config.getInstance().getSftpConfig().getBackupsWeight() == 0 ||
                storage.equals("googleDrive") && Config.getInstance().getGoogleDriveConfig().getBackupsNumber() == 0 && Config.getInstance().getGoogleDriveConfig().getBackupsWeight() == 0) {
            return;
        }

        HashSet<LocalDateTime> backupsToDeleteList = new HashSet<>();

        long backupsFolderByteSize = 0;

        ArrayList<Backup> backups = new ArrayList<>();
        if (storage.equals("local")) {
            backups.addAll(LocalBackup.getBackups());
        }
        if (storage.equals("sftp")) {
            backups.addAll(SftpBackup.getBackups());
        }
        if (storage.equals("ftp")) {
            backups.addAll(FtpBackup.getBackups());
        }
        if (storage.equals("googleDrive")) {
            backups.addAll(GoogleDriveBackup.getBackups());
        }

        if (cancelled) {
            return;
        }

        for (Backup backup : backups) {

            if (cancelled) {
                return;
            }

            try {
                backupsFolderByteSize += backup.getByteSize(sender);
            } catch (Exception e) {
                Logger.getLogger().warn("Failed to get backup byte size", sender);
                Logger.getLogger().warn(this.getClass(), e);
            }
        }

        ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();
        for (Backup backup : backups) {
            backupDateTimes.add(backup.getLocalDateTime());
        }
        Utils.sortLocalDateTime(backupDateTimes);

        if (storage.equals("local") && Config.getInstance().getLocalConfig().getBackupsNumber() != 0 ||
                storage.equals("sftp") && Config.getInstance().getSftpConfig().getBackupsNumber() != 0 ||
                storage.equals("ftp") && Config.getInstance().getFtpConfig().getBackupsNumber() != 0 ||
                storage.equals("googleDrive") && Config.getInstance().getGoogleDriveConfig().getBackupsNumber() != 0) {

            int backupsToDelete = backups.size();
            if (storage.equals("local")) {
                backupsToDelete -= Config.getInstance().getLocalConfig().getBackupsNumber();
            }
            if (storage.equals("sftp")) {
                backupsToDelete -= Config.getInstance().getSftpConfig().getBackupsNumber();
            }
            if (storage.equals("ftp")) {
                backupsToDelete -= Config.getInstance().getFtpConfig().getBackupsNumber();
            }
            if (storage.equals("googleDrive")) {
                backupsToDelete -= Config.getInstance().getGoogleDriveConfig().getBackupsNumber();
            }

            for (LocalDateTime fileName : backupDateTimes) {

                if (backupsToDelete <= 0) {
                    break;
                }

                if (backupsToDeleteList.contains(fileName)) {
                    continue;
                }

                for (Backup backup : backups) {

                    if (cancelled) {
                        return;
                    }

                    String backupFileName = backup.getName().replace(".zip", "");

                    try {
                        if (LocalDateTime.parse(backupFileName, Config.getInstance().getDateTimeFormatter()).equals(fileName)) {

                            Task deleteBackupTask = backup.getDeleteTask(false, sender);
                            deleteBackupTask.prepareTask();

                            deleteBackupTasks.add(deleteBackupTask);

                            backupsToDeleteList.add(fileName);
                            backupsFolderByteSize -= backup.getByteSize(sender);
                        }
                    } catch (Exception ignored) {
                    }
                }
                backupsToDelete--;
            }
        }

        if (storage.equals("local") && Config.getInstance().getLocalConfig().getBackupsWeight() != 0 ||
                storage.equals("sftp") && Config.getInstance().getSftpConfig().getBackupsWeight() != 0 ||
                storage.equals("ftp") && Config.getInstance().getFtpConfig().getBackupsWeight() != 0 ||
                storage.equals("googleDrive") && Config.getInstance().getGoogleDriveConfig().getBackupsWeight() != 0) {


            long bytesToDelete = backupsFolderByteSize;
            if (storage.equals("local")) {
                bytesToDelete -= Config.getInstance().getLocalConfig().getBackupsWeight();
            }
            if (storage.equals("sftp")) {
                bytesToDelete -= Config.getInstance().getSftpConfig().getBackupsWeight();
            }
            if (storage.equals("ftp")) {
                bytesToDelete -= Config.getInstance().getFtpConfig().getBackupsWeight();
            }
            if (storage.equals("googleDrive")) {
                bytesToDelete -= Config.getInstance().getGoogleDriveConfig().getBackupsWeight();
            }

            for (LocalDateTime fileName : backupDateTimes) {

                if (bytesToDelete <= 0) {
                    break;
                }

                if (backupsToDeleteList.contains(fileName)) {
                    continue;
                }

                for (Backup backup : backups) {

                    if (cancelled) {
                        return;
                    }

                    String backupFileName = backup.getName().replace(".zip", "");

                    try {
                        if (LocalDateTime.parse(backupFileName, Config.getInstance().getDateTimeFormatter()).equals(fileName)) {

                            bytesToDelete -= backup.getByteSize(sender);

                            Task deleteBackupTask = backup.getDeleteTask(false, sender);
                            deleteBackupTask.prepareTask();

                            deleteBackupTasks.add(deleteBackupTask);
                            backupsToDeleteList.add(fileName);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return getTaskMaxProgress();
        }

        long currentProgress = 0;

        for (Task deleteBackupTask : deleteBackupTasks) {
            currentProgress += deleteBackupTask.getTaskCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {

        long maxProgress = 0;

        for (Task deleteBackupTask : deleteBackupTasks) {
            maxProgress += deleteBackupTask.getTaskMaxProgress();
        }

        return maxProgress;
    }
}
