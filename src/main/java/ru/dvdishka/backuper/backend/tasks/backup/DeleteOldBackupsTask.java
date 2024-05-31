package ru.dvdishka.backuper.backend.tasks.backup;

import org.apache.commons.io.FileUtils;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.classes.SftpBackup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class DeleteOldBackupsTask extends Task {

    private static String taskName = "DeleteOldBackups";

    private ArrayList<Task> deleteBackupTasks = new ArrayList<>();

    public DeleteOldBackupsTask(boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);
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
                deleteDirTask.run();
            }

            Logger.getLogger().devLog("DeleteOldBackups task has been finished");

            if (setLocked) {

                Logger.getLogger().log("DeleteOldBackups completed", sender);
                UIUtils.successSound(sender);
                Backuper.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running DeleteOldBackups task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public void prepareTask() {

        isTaskPrepared = true;

        try {

            if (Config.getInstance().getLocalConfig().isEnabled()) {
                deleteBackups("local");
            }
            if (Config.getInstance().getSftpConfig().isEnabled()) {
                deleteBackups("sftp");
            }

        } catch (Exception e) {

            Logger.getLogger().warn("DeleteOldBackupsTask failed", sender);
            Logger.getLogger().warn(DeleteOldBackupsTask.class, e);
        }
    }

    private void deleteBackups(String storage) {

        HashSet<LocalDateTime> backupsToDeleteList = new HashSet<>();

        long backupsFolderByteSize = 0;
        if (storage.equals("local")) {
            backupsFolderByteSize = Utils.getFileFolderByteSize(new File(Config.getInstance().getLocalConfig().getBackupsFolder()));
        }
        if (storage.equals("sftp")) {
            backupsFolderByteSize = SftpUtils.getDirByteSize(Config.getInstance().getSftpConfig().getBackupsFolder(), sender);
        }

        if (storage.equals("local") && Config.getInstance().getLocalConfig().getBackupsNumber() != 0 ||
                storage.equals("sftp") && Config.getInstance().getSftpConfig().getBackupsNumber() != 0) {

            ArrayList<Backup> backups = new ArrayList<>();
            if (storage.equals("local")) {
                backups.addAll(LocalBackup.getBackups());
            }
            if (storage.equals("sftp")) {
                backups.addAll(SftpBackup.getBackups());
            }

            ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();
            for (Backup backup : backups) {
                backupDateTimes.add(backup.getLocalDateTime());
            }
            Utils.sortLocalDateTime(backupDateTimes);

            int backupsToDelete = backups.size();
            if (storage.equals("local")) {
                backupsToDelete -= Config.getInstance().getLocalConfig().getBackupsNumber();
            }
            if (storage.equals("sftp")) {
                backupsToDelete -= Config.getInstance().getSftpConfig().getBackupsNumber();
            }

            for (LocalDateTime fileName : backupDateTimes) {

                if (backupsToDelete <= 0) {
                    break;
                }

                if (backupsToDeleteList.contains(fileName)) {
                    continue;
                }

                for (Backup backup : backups) {

                    String backupFileName = backup.getName().replace(".zip", "");

                    try {
                        if (LocalDateTime.parse(backupFileName, LocalBackup.dateTimeFormatter).equals(fileName)) {

                            Task deleteBackupTask = backup.getDeleteTask(false, sender);
                            deleteBackupTask.prepareTask();

                            deleteBackupTasks.add(deleteBackupTask);

                            backupsToDeleteList.add(fileName);
                            backupsFolderByteSize -= backup.getByteSize(sender);
                        }
                    } catch (Exception ignored) {}
                }
                backupsToDelete--;
            }
        }

        if (Config.getInstance().getLocalConfig().getBackupsWeight() != 0) {

            if (storage.equals("local") && Config.getInstance().getLocalConfig().getBackupsWeight() != 0 ||
                    storage.equals("sftp") && Config.getInstance().getSftpConfig().getBackupsWeight() != 0) {

                ArrayList<Backup> backups = new ArrayList<>();
                if (storage.equals("local")) {
                    backups.addAll(LocalBackup.getBackups());
                }
                if (storage.equals("sftp")) {
                    backups.addAll(SftpBackup.getBackups());
                }

                ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();
                for (Backup backup : backups) {
                    backupDateTimes.add(backup.getLocalDateTime());
                }
                Utils.sortLocalDateTime(backupDateTimes);

                long bytesToDelete = backupsFolderByteSize;
                if (storage.equals("local")) {
                    bytesToDelete -= Config.getInstance().getLocalConfig().getBackupsWeight();
                }
                if (storage.equals("sftp")) {
                    bytesToDelete -= Config.getInstance().getSftpConfig().getBackupsWeight();
                }

                for (LocalDateTime fileName : backupDateTimes) {

                    if (bytesToDelete <= 0) {
                        break;
                    }

                    if (backupsToDeleteList.contains(fileName)) {
                        continue;
                    }

                    for (Backup backup : backups) {

                        String backupFileName = backup.getName().replace(".zip", "");

                        try {
                            if (LocalDateTime.parse(backupFileName, LocalBackup.dateTimeFormatter).equals(fileName)) {

                                bytesToDelete -= backup.getByteSize(sender);

                                Task deleteBackupTask = backup.getDeleteTask(false, sender);
                                deleteBackupTask.prepareTask();

                                deleteBackupTasks.add(deleteBackupTask);
                                backupsToDeleteList.add(fileName);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {

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
