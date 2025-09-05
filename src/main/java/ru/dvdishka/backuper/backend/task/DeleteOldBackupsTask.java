package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;

public class DeleteOldBackupsTask extends BaseTask {

    private final ArrayList<BaseTask> deleteBackupTasks = new ArrayList<>();

    public DeleteOldBackupsTask() {
        super();
    }

    @Override
    public void run() throws IOException {

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
    public void prepareTask(CommandSender sender) {

        try {
            if (!cancelled && ConfigManager.getInstance().getLocalConfig().isEnabled()) {
                prepareStorage("local");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }

        try {
            if (!cancelled && ConfigManager.getInstance().getFtpConfig().isEnabled()) {
                prepareStorage("ftp");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }

        try {
            if (!cancelled && ConfigManager.getInstance().getSftpConfig().isEnabled()) {
                prepareStorage("sftp");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }

        try {
            if (!cancelled && ConfigManager.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection()) {
                prepareStorage("googleDrive");
            }
        } catch (Exception e) {
            warn("Failed to prepare %s storage for old backups deletion");
            warn(e);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;

        for (BaseTask deleteDirTask : deleteBackupTasks) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteDirTask);
        }
    }

    private void prepareStorage(String storage) {

        if (cancelled ||
                storage.equals("local") && ConfigManager.getInstance().getLocalConfig().getBackupsNumber() == 0 && ConfigManager.getInstance().getLocalConfig().getBackupsWeight() == 0 ||
                storage.equals("ftp") && ConfigManager.getInstance().getFtpConfig().getBackupsNumber() == 0 && ConfigManager.getInstance().getFtpConfig().getBackupsWeight() == 0 ||
                storage.equals("sftp") && ConfigManager.getInstance().getSftpConfig().getBackupsNumber() == 0 && ConfigManager.getInstance().getSftpConfig().getBackupsWeight() == 0 ||
                storage.equals("googleDrive") && ConfigManager.getInstance().getGoogleDriveConfig().getBackupsNumber() == 0 && ConfigManager.getInstance().getGoogleDriveConfig().getBackupsWeight() == 0) {
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

        if (storage.equals("local") && ConfigManager.getInstance().getLocalConfig().getBackupsNumber() != 0 ||
                storage.equals("sftp") && ConfigManager.getInstance().getSftpConfig().getBackupsNumber() != 0 ||
                storage.equals("ftp") && ConfigManager.getInstance().getFtpConfig().getBackupsNumber() != 0 ||
                storage.equals("googleDrive") && ConfigManager.getInstance().getGoogleDriveConfig().getBackupsNumber() != 0) {

            int backupsToDelete = backups.size();
            if (storage.equals("local")) {
                backupsToDelete -= ConfigManager.getInstance().getLocalConfig().getBackupsNumber();
            }
            if (storage.equals("sftp")) {
                backupsToDelete -= ConfigManager.getInstance().getSftpConfig().getBackupsNumber();
            }
            if (storage.equals("ftp")) {
                backupsToDelete -= ConfigManager.getInstance().getFtpConfig().getBackupsNumber();
            }
            if (storage.equals("googleDrive")) {
                backupsToDelete -= ConfigManager.getInstance().getGoogleDriveConfig().getBackupsNumber();
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
                        if (LocalDateTime.parse(backupFileName, ConfigManager.getInstance().getDateTimeFormatter()).equals(fileName)) {

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

        if (storage.equals("local") && ConfigManager.getInstance().getLocalConfig().getBackupsWeight() != 0 ||
                storage.equals("sftp") && ConfigManager.getInstance().getSftpConfig().getBackupsWeight() != 0 ||
                storage.equals("ftp") && ConfigManager.getInstance().getFtpConfig().getBackupsWeight() != 0 ||
                storage.equals("googleDrive") && ConfigManager.getInstance().getGoogleDriveConfig().getBackupsWeight() != 0) {


            long bytesToDelete = backupsFolderByteSize;
            if (storage.equals("local")) {
                bytesToDelete -= ConfigManager.getInstance().getLocalConfig().getBackupsWeight();
            }
            if (storage.equals("sftp")) {
                bytesToDelete -= ConfigManager.getInstance().getSftpConfig().getBackupsWeight();
            }
            if (storage.equals("ftp")) {
                bytesToDelete -= ConfigManager.getInstance().getFtpConfig().getBackupsWeight();
            }
            if (storage.equals("googleDrive")) {
                bytesToDelete -= ConfigManager.getInstance().getGoogleDriveConfig().getBackupsWeight();
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
                        if (LocalDateTime.parse(backupFileName, ConfigManager.getInstance().getDateTimeFormatter()).equals(fileName)) {

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
