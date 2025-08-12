package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;

public class DeleteOldBackupsTask extends BaseAsyncTask {

    private final ArrayList<BaseTask> deleteBackupTasks = new ArrayList<>();

    public DeleteOldBackupsTask() {
        super();
    }

    @Override
    protected void run() throws IOException {

        for (BaseTask deleteDirTask : deleteBackupTasks) {
            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(deleteDirTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(deleteDirTask, e));
                }
            }
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {

        try {
            if (!cancelled && Config.getInstance().getLocalConfig().isEnabled()) {
                prepareStorage("local");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }

        try {
            if (!cancelled && Config.getInstance().getFtpConfig().isEnabled()) {
                prepareStorage("ftp");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }

        try {
            if (!cancelled && Config.getInstance().getSftpConfig().isEnabled()) {
                prepareStorage("sftp");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }

        try {
            if (!cancelled && Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection()) {
                prepareStorage("googleDrive");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (BaseTask deleteDirTask : deleteBackupTasks) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteDirTask);
        }
    }

    private void prepareStorage(String storage) {

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
                backupsFolderByteSize += backup.getByteSize();
            } catch (Exception e) {
                warn("Failed to get backup byte size", sender);
                warn(e);
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

                            BaseTask deleteBackupTask = backup.getDeleteTask();
                            Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);

                            deleteBackupTasks.add(deleteBackupTask);

                            backupsToDeleteList.add(fileName);
                            backupsFolderByteSize -= backup.getByteSize();
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

                            bytesToDelete -= backup.getByteSize();

                            BaseTask deleteBackupTask = backup.getDeleteTask();
                            Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);

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

        for (BaseTask deleteBackupTask : deleteBackupTasks) {
            currentProgress += deleteBackupTask.getTaskCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {

        long maxProgress = 0;

        for (BaseTask deleteBackupTask : deleteBackupTasks) {
            maxProgress += deleteBackupTask.getTaskMaxProgress();
        }

        return maxProgress;
    }
}
