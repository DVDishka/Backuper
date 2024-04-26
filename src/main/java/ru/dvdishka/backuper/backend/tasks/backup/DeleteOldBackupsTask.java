package ru.dvdishka.backuper.backend.tasks.backup;

import org.apache.commons.io.FileUtils;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.folder.DeleteDirTask;
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
            Backup.lock(this);
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
                Backup.unlock();
            }
        } catch (Exception e) {

            if (setLocked) {
                Backup.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running DeleteOldBackups task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public void prepareTask() {

        isTaskPrepared = true;

        try {

            File backupsDir = new File(Config.getInstance().getBackupsFolder());
            HashSet<LocalDateTime> backupsToDeleteList = new HashSet<>();
            long backupsFolderWeight = FileUtils.sizeOf(backupsDir);

            if (Config.getInstance().getBackupsNumber() != 0 && backupsDir.listFiles() != null) {

                ArrayList<LocalDateTime> backups = Backup.getBackups();
                Utils.sortLocalDateTime(backups);

                int backupsToDelete = backups.size() - Config.getInstance().getBackupsNumber();

                for (LocalDateTime fileName : backups) {

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
                            if (LocalDateTime.parse(backupFileName, Backup.dateTimeFormatter).equals(fileName)) {

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

            if (Config.getInstance().getBackupsWeight() != 0) {

                if (backupsFolderWeight > Config.getInstance().getBackupsWeight() && backupsDir.listFiles() != null) {

                    ArrayList<LocalDateTime> backups = Backup.getBackups();
                    Utils.sortLocalDateTime(backups);

                    long bytesToDelete = backupsFolderWeight - Config.getInstance().getBackupsWeight();

                    for (LocalDateTime fileName : backups) {

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
                                if (LocalDateTime.parse(backupFileName, Backup.dateTimeFormatter).equals(fileName)) {

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
