package ru.dvdishka.backuper.backend.tasks.backup;

import org.apache.commons.io.FileUtils;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class DeleteOldBackupsTask extends Task {

    private static String taskName = "DeleteOldBackups";

    private ArrayList<DeleteDirTask> deleteDirTasks = new ArrayList<>();

    public DeleteOldBackupsTask(boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {
            Logger.getLogger().devLog("DeleteOldBackupsTask has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            for (DeleteDirTask deleteDirTask : deleteDirTasks) {
                deleteDirTask.run();
            }

            Logger.getLogger().devLog("DeleteOldBackupsTask has been finished");

            if (setLocked) {
                Backuper.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
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

            File backupsDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
            HashSet<LocalDateTime> backupsToDeleteList = new HashSet<>();
            long backupsFolderWeight = FileUtils.sizeOf(backupsDir);

            if (Config.getInstance().getLocalConfig().getBackupsNumber() != 0 && backupsDir.listFiles() != null) {

                ArrayList<LocalBackup> backups = LocalBackup.getBackups();
                ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();

                for (LocalBackup backup : backups) {
                    backupDateTimes.add(backup.getLocalDateTime());
                }

                Utils.sortLocalDateTime(backupDateTimes);

                int backupsToDelete = backups.size() - Config.getInstance().getLocalConfig().getBackupsNumber();

                for (LocalDateTime fileName : backupDateTimes) {

                    if (backupsToDelete <= 0) {
                        break;
                    }

                    if (backupsToDeleteList.contains(fileName)) {
                        continue;
                    }

                    for (File backup : Objects.requireNonNull(backupsDir.listFiles())) {

                        String backupFileName = backup.getName().replace(".zip", "");

                        while (backupFileName.length() < fileName.toString().length()) {

                            backupFileName = backupFileName.concat("0");
                        }

                        try {
                            if (LocalDateTime.parse(backupFileName, LocalBackup.dateTimeFormatter).equals(fileName)) {

                                DeleteDirTask deleteDirTask = new DeleteDirTask(backup, false, sender);
                                deleteDirTask.prepareTask();
                                deleteDirTasks.add(deleteDirTask);
                                backupsToDeleteList.add(fileName);
                                backupsFolderWeight -= FileUtils.sizeOf(backup);
                            }
                        } catch (Exception ignored) {}
                    }
                    backupsToDelete--;
                }
            }

            if (Config.getInstance().getLocalConfig().getBackupsWeight() != 0) {

                if (backupsFolderWeight > Config.getInstance().getLocalConfig().getBackupsWeight() && backupsDir.listFiles() != null) {

                    ArrayList<LocalBackup> backups = LocalBackup.getBackups();
                    ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();

                    for (LocalBackup backup : backups) {
                        backupDateTimes.add(backup.getLocalDateTime());
                    }

                    Utils.sortLocalDateTime(backupDateTimes);

                    long bytesToDelete = backupsFolderWeight - Config.getInstance().getLocalConfig().getBackupsWeight();

                    for (LocalDateTime fileName : backupDateTimes) {

                        if (bytesToDelete <= 0) {
                            break;
                        }

                        if (backupsToDeleteList.contains(fileName)) {
                            continue;
                        }

                        if (backupsDir.listFiles() == null) {

                            Logger.getLogger().warn("Something went wrong while trying to delete old backup!", sender);
                        }

                        for (File backup : Objects.requireNonNull(backupsDir.listFiles())) {

                            String backupFileName = backup.getName().replace(".zip", "");

                            try {
                                if (LocalDateTime.parse(backupFileName, LocalBackup.dateTimeFormatter).equals(fileName)) {

                                    bytesToDelete -= FileUtils.sizeOf(backup);
                                    DeleteDirTask deleteDirTask = new DeleteDirTask(backup, false, sender);
                                    deleteDirTask.prepareTask();
                                    deleteDirTasks.add(deleteDirTask);
                                    backupsToDeleteList.add(fileName);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (Exception e) {

            Logger.getLogger().warn("DeleteOldBackupsTask failed", sender);
            Logger.getLogger().warn(DeleteOldBackupsTask.class, e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        long currentProgress = 0;

        for (DeleteDirTask deleteDirTask : deleteDirTasks) {
            currentProgress += deleteDirTask.getTaskCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {

        long maxProgress = 0;

        for (DeleteDirTask deleteDirTask : deleteDirTasks) {
            maxProgress += deleteDirTask.getTaskMaxProgress();
        }

        return maxProgress;
    }
}
