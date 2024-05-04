package ru.dvdishka.backuper.backend.tasks.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.folder.CopyFilesToDirTask;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.zip.AddDirToZipTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.zip.ZipOutputStream;

public class BackupTask extends Task {

    private static final String taskName = "Backup";

    private boolean isAutoBackup = false;
    private String afterBackup = "NOTHING";

    private final long deleteProgressMultiplier = 1;
    private final long copyProgressMultiplier = 5;
    private final long zipProgressMultiplier = 10;

    private File backupDir;
    private File backupsDir;
    private ZipOutputStream targetZipOutputStream = null;

    private ArrayList<Task> tasks = new ArrayList<>();

    public BackupTask(String afterBackup, boolean isAutoBackup, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.afterBackup = afterBackup;
        this.isAutoBackup = isAutoBackup;
    }

    @Override
    public void run() {

        if (setLocked) {

            if (Backup.isLocked()) {
                Logger.getLogger().warn("Failed to start Backup task because it is blocked by another operation", sender);
                return;
            }

            Backup.lock(this);
        }

        try {

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (Config.getInstance().isSkipDuplicateBackup() && isAutoBackup && Config.getInstance().getLastBackup() >= Config.getInstance().getLastChange()) {

                Logger.getLogger().log("The backup cycle will be skipped since there were no changes from the previous backup", sender);
                Config.getInstance().updateLastBackup();

                if (afterBackup.equals("RESTART")) {

                    Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {
                        Scheduler.cancelTasks(Utils.plugin);
                        Bukkit.getServer().spigot().restart();
                    }, 20);

                } else if (afterBackup.equals("STOP")) {

                    Logger.getLogger().devLog("Stopping server...");
                    Bukkit.shutdown();
                }

                if (setLocked) {
                    UIUtils.successSound(sender);
                    Backup.unlock();
                }
                return;
            }

            Logger.getLogger().devLog("Backup task has been started");

            for (Task task : tasks) {
                task.run();
            }

            if (Config.getInstance().isZipArchive()) {
                targetZipOutputStream.close();
            }

            {
                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP task has been started");
                if (Config.getInstance().isZipArchive()) {

                    if (!new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName() + ".zip").toFile()
                            .renameTo(new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "") + ".zip").toFile())) {
                        Logger.getLogger().warn("The Rename \"in progress\" ZIP task has been finished with an exception!", sender);
                    }
                } else {
                    if (!new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile()
                            .renameTo(new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "")).toFile())) {
                        Logger.getLogger().warn("The Rename \"in progress\" ZIP task has been finished with an exception!", sender);
                    }
                }
                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP task has been finished");
            }

            {
                if (isAutoBackup) {
                    Logger.getLogger().devLog("Update \"lastBackup\" Variable task has been started");
                    Config.getInstance().updateLastBackup();
                    Logger.getLogger().devLog("Update \"lastBackup\" Variable task has been finished");
                }
            }

            if (setLocked) {
                UIUtils.successSound(sender);
                Backup.unlock();
            }
            Logger.getLogger().success("Backup process has been finished successfully!", sender);

            if (afterBackup.equals("RESTART")) {

                Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {
                    Scheduler.cancelTasks(Utils.plugin);
                    Bukkit.getServer().spigot().restart();
                }, 20);

            } else if (afterBackup.equals("STOP")) {

                Logger.getLogger().devLog("Stopping server...");
                Bukkit.shutdown();
            }

            Logger.getLogger().devLog("Backup task has been finished");

        } catch (Exception e) {

            try {
                targetZipOutputStream.close();
            } catch (Exception ignored) {}

            Logger.getLogger().warn("Something went wrong while running the task: " + taskName);
            Logger.getLogger().warn(e.getMessage());

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backup.unlock();
            }
        }
    }

    @Override
    public void prepareTask() {

        try {

            this.isTaskPrepared = true;

            this.backupDir = new File(Config.getInstance().getBackupsFolder()).toPath().resolve(
                    LocalDateTime.now().format(Backup.dateTimeFormatter) + " in progress").toFile();
            this.backupsDir = new File(Config.getInstance().getBackupsFolder());

            {
                if (!Config.getInstance().isZipArchive() && !backupDir.mkdir()) {

                    Logger.getLogger().warn("Can not create " + backupDir.getPath() + " dir!", sender);
                }

                tasks.add(new SetWorldsReadOnlyTask(false, sender));

                if (Config.getInstance().isZipArchive()) {
                    targetZipOutputStream = new ZipOutputStream(new FileOutputStream(backupDir.getPath() + ".zip"));
                }

                for (World world : Bukkit.getWorlds()) {

                    File worldDir = world.getWorldFolder();

                    try {

                        if (Config.getInstance().isZipArchive()) {

                            Task task = new AddDirToZipTask(worldDir, targetZipOutputStream, true, false, false, sender);
                            task.prepareTask();

                            tasks.add(task);

                        } else {

                            Task task = new CopyFilesToDirTask(worldDir, backupDir, false, sender);
                            task.prepareTask();

                            tasks.add(task);
                        }

                    } catch (Exception e) {

                        Logger.getLogger().warn("Something went wrong when trying to copy files!", sender);
                        Logger.getLogger().warn(this, e);
                    }
                }

                for (String additionalDirectoryToBackup : Config.getInstance().getAddDirectoryToBackup()) {

                    try {

                        File additionalDirectoryToBackupFile = Paths.get(additionalDirectoryToBackup).toFile();
                        boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);

                        if (!additionalDirectoryToBackupFile.exists()) {
                            Logger.getLogger().warn("addDirectoryToBackup \"" + additionalDirectoryToBackupFile.getPath() + "\" does not exist!");
                            continue;
                        }

                        if (isExcludedDirectory) {
                            continue;
                        }

                        if (Config.getInstance().isZipArchive()) {

                            Task task = new AddDirToZipTask(additionalDirectoryToBackupFile, targetZipOutputStream, true, false, false, sender);
                            task.prepareTask();

                            tasks.add(task);

                        } else {

                            Task task;
                            if (additionalDirectoryToBackupFile.isDirectory()) {
                                task = new CopyFilesToDirTask(additionalDirectoryToBackupFile, backupDir, false, sender);
                            } else {
                                task = new CopyFilesToDirTask(additionalDirectoryToBackupFile, backupDir, false, sender);
                            }
                            task.prepareTask();

                            tasks.add(task);
                        }

                    } catch (Exception e) {
                        Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + additionalDirectoryToBackup + "\"", sender);
                        Logger.getLogger().warn(this, e);
                    }
                }
            }

            {
                tasks.add(new SetWorldsWritableTask(false, sender));
            }

            // Delete old backups task
            {
                Task task = new DeleteOldBackupsTask(false, sender);
                task.prepareTask();

                tasks.add(task);
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
            }

            new SetWorldsWritableTask(false, sender).run();

            Logger.getLogger().warn("The Backup task has been finished with an exception!", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        long currentProgress = 0;

        for (Task task : tasks) {

            long currentTaskProgress = task.getTaskCurrentProgress();
            long taskProgressMultiplier = 1;

            if (task instanceof DeleteOldBackupsTask) {
                taskProgressMultiplier = deleteProgressMultiplier;
            }
            if (task instanceof CopyFilesToDirTask) {
                taskProgressMultiplier = copyProgressMultiplier;
            }
            if (task instanceof AddDirToZipTask) {
                taskProgressMultiplier = zipProgressMultiplier;
            }

            currentProgress += currentTaskProgress * taskProgressMultiplier;
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {

        long maxProgress = 0;

        for (Task task : tasks) {

            long maxTaskProgress = task.getTaskMaxProgress();
            long taskProgressMultiplier = 1;

            if (task instanceof DeleteOldBackupsTask) {
                taskProgressMultiplier = deleteProgressMultiplier;
            }
            if (task instanceof CopyFilesToDirTask) {
                taskProgressMultiplier = copyProgressMultiplier;
            }
            if (task instanceof AddDirToZipTask) {
                taskProgressMultiplier = zipProgressMultiplier;
            }

            maxProgress += maxTaskProgress * taskProgressMultiplier;
        }

        return maxProgress;
    }
}
