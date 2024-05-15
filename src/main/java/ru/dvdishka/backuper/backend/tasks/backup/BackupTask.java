package ru.dvdishka.backuper.backend.tasks.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.local.folder.CopyFilesToFolderTask;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.zip.tozip.AddDirToZipTask;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpSendFileFolderTask;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
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
    private String backupName;
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
            Backuper.lock(this);
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
                    Backuper.unlock();
                }
                return;
            }

            Logger.getLogger().devLog("Backup task has been started");

            for (Task task : tasks) {
                task.run();
            }

            if (Config.getInstance().getLocalConfig().isEnabled()) {

                if (Config.getInstance().getLocalConfig().isZipArchive()) {
                    try {
                        targetZipOutputStream.close();
                    } catch (Exception ignored) {
                    }
                }

                // RENAME LOCAL TASK
                {
                    Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
                    if (Config.getInstance().getLocalConfig().isZipArchive()) {

                        if (!new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName() + ".zip").toFile()
                                .renameTo(new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "") + ".zip").toFile())) {
                            Logger.getLogger().warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
                        }
                    } else {
                        if (!new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile()
                                .renameTo(new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "")).toFile())) {
                            Logger.getLogger().warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
                        }
                    }
                    Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
                }
            }

            // RENAME SFTP TASK
            if (Config.getInstance().getSftpConfig().isEnabled()) {

                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP SFTP task has been started");

                SftpUtils.renameRemoteFile(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                                backupName), SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                                backupName.replace(" in progress", "")), sender);

                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP SFTP task has been finished");
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
                Backuper.unlock();
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
                Backuper.unlock();
            }
        }
    }

    @Override
    public void prepareTask() {

        try {

            this.isTaskPrepared = true;

            this.backupName = LocalDateTime.now().format(LocalBackup.dateTimeFormatter) + " in progress";

            if (Config.getInstance().getLocalConfig().isEnabled()) {
                prepareLocalTask();
            }
            if (Config.getInstance().getSftpConfig().isEnabled()) {
                prepareSftpTask();
            }

            tasks.add(new SetWorldsWritableTask(false, sender));

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
            }

            new SetWorldsWritableTask(false, sender).run();

            Logger.getLogger().warn("The Backup task has been finished with an exception!", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    private void prepareLocalTask() {

        try {

            this.backupDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupName).toFile();
            this.backupsDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

            if (!Config.getInstance().getLocalConfig().isZipArchive() && !backupDir.mkdir()) {

                Logger.getLogger().warn("Can not create " + backupDir.getPath() + " dir!", sender);
            }

            tasks.add(new SetWorldsReadOnlyTask(false, sender));

            if (Config.getInstance().getLocalConfig().isZipArchive()) {
                targetZipOutputStream = new ZipOutputStream(new FileOutputStream(backupDir.getPath() + ".zip"));
            }

            for (World world : Bukkit.getWorlds()) {

                File worldDir = world.getWorldFolder();

                try {

                    if (Config.getInstance().getLocalConfig().isZipArchive()) {

                        Task task = new AddDirToZipTask(worldDir, targetZipOutputStream, true, false, false, sender);
                        task.prepareTask();

                        tasks.add(task);

                    } else {

                        Task task = new CopyFilesToFolderTask(worldDir, backupDir, true, false, false, sender);
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

                    if (Config.getInstance().getLocalConfig().isZipArchive()) {

                        Task task = new AddDirToZipTask(additionalDirectoryToBackupFile, targetZipOutputStream, true, false, false, sender);
                        task.prepareTask();

                        tasks.add(task);

                    } else {

                        Task task = new CopyFilesToFolderTask(additionalDirectoryToBackupFile, backupDir, true, false, false, sender);
                        task.prepareTask();

                        tasks.add(task);
                    }

                } catch (Exception e) {
                    Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + additionalDirectoryToBackup + "\"", sender);
                    Logger.getLogger().warn(this, e);
                }
            }

            Task task = new DeleteOldBackupsTask(false, sender);
            task.prepareTask();
            tasks.add(task);

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to prepare local task");
            Logger.getLogger().warn(this, e);
        }
    }

    private void prepareSftpTask() {

        try {

            SftpUtils.createRemoteFolder(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                            backupName), sender);

            for (World world : Bukkit.getWorlds()) {

                File worldDir = world.getWorldFolder();

                try {
                    Task task = new SftpSendFileFolderTask(worldDir, SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                            backupName), Config.getInstance().getSftpConfig().getPathSeparatorSymbol(), true,
                            false, Config.getInstance().getSftpConfig().getAuthType(),
                            Config.getInstance().getSftpConfig().getUsername(), Config.getInstance().getSftpConfig().getAddress(),
                            Config.getInstance().getSftpConfig().getPort(), Config.getInstance().getSftpConfig().getPassword(),
                            Config.getInstance().getSftpConfig().getKeyFilePath(), Config.getInstance().getSftpConfig().getUseKnownHostsFile(),
                            Config.getInstance().getSftpConfig().getKnownHostsFilePath(), false, sender);
                    task.prepareTask();

                    tasks.add(task);

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong when trying to copy files!", sender);
                    Logger.getLogger().warn(this, e);
                }
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to prepare sftp task");
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
            if (task instanceof CopyFilesToFolderTask) {
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
            if (task instanceof CopyFilesToFolderTask) {
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
