package ru.dvdishka.backuper.backend.tasks.common;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.StorageType;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpAddLocalDirsToZipTask;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpSendFileFolderTask;
import ru.dvdishka.backuper.backend.tasks.googleDrive.GoogleDriveSendFileFolderTask;
import ru.dvdishka.backuper.backend.tasks.local.folder.CopyFilesToFolderTask;
import ru.dvdishka.backuper.backend.tasks.local.zip.tozip.AddDirToZipTask;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpSendFileFolderTask;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BackupTask extends Task {

    private static final String taskName = "Backup";

    private final boolean isAutoBackup;
    private final String afterBackup;
    private final boolean isLocal;
    private boolean isFtp;
    private boolean isSftp;
    private boolean isGoogleDrive;

    private final long deleteProgressMultiplier = 3;
    private final long copyProgressMultiplier = 5;
    private final long zipProgressMultiplier = 10;
    private final long sendSftpProgressMultiplier = 15;
    private final long sendFtpProgressMultiplier = 15;
    private final long sendGoogleDriveProgressMultiplier = 40;
    private final long zipFtpProgressMultiplier = 20;

    /**
     * May contain " in progress" part
     */
    private String backupName;

    private List<Task> tasks = new ArrayList<>();

    /**
     * Storage will be used if it is enabled and autoBackup for this storage is enabled too
     **/
    public BackupTask(String afterBackup, boolean isAutoBackup, boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
        this.afterBackup = afterBackup.toUpperCase();
        this.isAutoBackup = isAutoBackup;
        this.isLocal = Config.getInstance().getLocalConfig().isAutoBackup() && Config.getInstance().getLocalConfig().isEnabled();
        this.isFtp = Config.getInstance().getFtpConfig().isAutoBackup() && Config.getInstance().getFtpConfig().isEnabled();
        this.isSftp = Config.getInstance().getSftpConfig().isAutoBackup() && Config.getInstance().getSftpConfig().isEnabled();
        this.isGoogleDrive = Config.getInstance().getGoogleDriveConfig().isAutoBackup() && Config.getInstance().getGoogleDriveConfig().isEnabled();
    }

    public BackupTask(String afterBackup, boolean isAutoBackup, boolean isLocal, boolean isFtp, boolean isSftp, boolean isGoogleDrive, boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
        this.afterBackup = afterBackup.toUpperCase();
        this.isAutoBackup = isAutoBackup;
        this.isLocal = isLocal;
        this.isFtp = isFtp;
        this.isSftp = isSftp;
        this.isGoogleDrive = isGoogleDrive;
    }


    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {

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

            if (!cancelled && !isTaskPrepared) {
                prepareTask();
            }

            if (isAutoBackup) {
                Logger.getLogger().log("Auto backup task has been started", sender);
            }

            if (!cancelled && isFtp && !FtpUtils.checkConnection(sender)) {
                Logger.getLogger().warn("Failed to connect to FTP(S) server during the backup task. Skipping this storage...", sender);
                isFtp = false;
            }
            if (!cancelled && isSftp && !SftpUtils.checkConnection(sender)) {
                Logger.getLogger().warn("Failed to connect to SFTP server during the backup task. Skipping this storage...", sender);
                isSftp = false;
            }
            if (!cancelled && isGoogleDrive && !GoogleDriveUtils.isAuthorized(sender)) {
                Logger.getLogger().warn("Failed to connect to Google Drive or Google account is not linked!", sender);
                isGoogleDrive = false;
            }

            Logger.getLogger().devLog("Backup task has been started");

            long localBackupByteSize = 0, ftpBackupByteSize = 0, sftpBackupByteSize = 0, googleDriveBackupByteSize = 0;

            List<CompletableFuture<Void>> taskFutures = new ArrayList<>();

            // Lock world folders if it is necessary
            if (!cancelled) {
                new SetWorldsReadOnlyTask(false, permissions, sender).run();
            }
            for (Task task : tasks) {
                // Check if storage is enabled
                if ((task instanceof FtpAddLocalDirsToZipTask || task instanceof FtpSendFileFolderTask) && !isFtp) {
                    continue;
                }
                if (task instanceof SftpSendFileFolderTask && !isSftp) {
                    continue;
                }
                if (task instanceof GoogleDriveSendFileFolderTask && !isGoogleDrive) {
                    continue;
                }
                if ((task instanceof CopyFilesToFolderTask || task instanceof AddDirToZipTask) && !isLocal) {
                    continue;
                }

                // Start task paralleled
                if (!cancelled) {
                    taskFutures.add(CompletableFuture.runAsync(task::run));
                }

                // Calculate new backup size
                if (!cancelled && (task instanceof CopyFilesToFolderTask)) {
                    localBackupByteSize += task.getTaskMaxProgress();
                }
                if (!cancelled && (task instanceof FtpSendFileFolderTask)) {
                    ftpBackupByteSize += task.getTaskMaxProgress();
                }
                if (!cancelled && (task instanceof SftpSendFileFolderTask)) {
                    sftpBackupByteSize += task.getTaskMaxProgress();
                }
                if (!cancelled && (task instanceof GoogleDriveSendFileFolderTask)) {
                    googleDriveBackupByteSize += task.getTaskMaxProgress();
                }
            }

            // Wait for all tasks
            CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join();

            // Unlock world folders anyway
            new SetWorldsWritableTask(false, permissions, sender).run();

            if (!cancelled && isLocal) {

                File backupDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(Config.getInstance().getLocalConfig().isZipArchive() ? backupName + ".zip" :  backupName).toFile();

                // RENAME LOCAL TASK
                {

                    Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
                    if (Config.getInstance().getLocalConfig().isZipArchive()) {

                        File oldZipFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile();
                        File newZipFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "")).toFile();

                        int attempts = 0;
                        while (!oldZipFile.renameTo(newZipFile) && attempts < 1000000) {
                            if (attempts == 999999) {
                                Logger.getLogger().warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
                            }
                            attempts++;
                        }
                    } else {

                        File oldFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile();
                        File newFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "")).toFile();

                        int attempts = 0;
                        while (!oldFolder.renameTo(newFolder) && attempts < 1000000) {
                            if (attempts == 999999) {
                                Logger.getLogger().warn("The Rename \"in progress\" Folder local task has been finished with an exception!", sender);
                            }
                            attempts++;
                        }

                        // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED)
                        Backup.addCalculatedBackupSize(StorageType.LOCAL, backupName.replace(" in progress", ""), localBackupByteSize);
                        Logger.getLogger().devLog("New LOCAL backup size has been cached");
                    }
                    Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
                }
            }

            // RENAME FTP TASK
            if (!cancelled && isFtp) {

                Logger.getLogger().devLog("The Rename \"in progress\" Folder FTP(S) task has been started");

                String fileType = "";

                if (Config.getInstance().getFtpConfig().isZipArchive()) {
                    fileType = ".zip";
                }

                FtpUtils.renameFile(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(),
                        backupName + fileType), FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(),
                        backupName.replace(" in progress", "") + fileType), sender);

                Logger.getLogger().devLog("The Rename \"in progress\" Folder FTP(S) task has been finished");

                if (!Config.getInstance().getFtpConfig().isZipArchive()) {
                    // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED)
                    Backup.addCalculatedBackupSize(StorageType.FTP, backupName.replace(" in progress", ""), ftpBackupByteSize);
                    Logger.getLogger().devLog("New SFTP backup size has been cached");
                }
            }

            // RENAME SFTP TASK
            if (!cancelled && isSftp) {

                Logger.getLogger().devLog("The Rename \"in progress\" Folder SFTP task has been started");

                SftpUtils.renameFile(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                        backupName), SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                        backupName.replace(" in progress", "")), sender);

                Logger.getLogger().devLog("The Rename \"in progress\" Folder SFTP task has been finished");

                // Add new backup size to cache
                Backup.addCalculatedBackupSize(StorageType.SFTP, backupName.replace(" in progress", ""), sftpBackupByteSize);
                Logger.getLogger().devLog("New SFTP backup size has been cached");
            }

            // RENAME GOOGLE DRIVE TASK
            if (!cancelled && isGoogleDrive) {

                Logger.getLogger().devLog("The Rename \"in progress\" Folder GoogleDrive task has been started");

                try {
                    GoogleDriveUtils.renameFile(GoogleDriveUtils.getFileByName(backupName, Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), sender).getId(), backupName.replace(" in progress", ""), sender);
                } catch (Exception e) {
                    Logger.getLogger().warn("Failed to rename Google Drive file " + backupName, sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }

                Logger.getLogger().devLog("The Rename \"in progress\" Folder GoogleDrive task has been finished");

                // Add new backup size to cache
                Backup.addCalculatedBackupSize(StorageType.GOOGLE_DRIVE, backupName.replace(" in progress", ""), googleDriveBackupByteSize);
                Logger.getLogger().devLog("New GOOGLE_DRIVE backup size has been cached");
            }

            // UPDATE VARIABLES
            if (!cancelled && isAutoBackup) {
                Logger.getLogger().devLog("Update \"lastBackup\" Variable task has been started");
                Config.getInstance().updateLastBackup();
                Logger.getLogger().devLog("Update \"lastBackup\" Variable task has been finished");
            }

            // DELETE OLD BACKUPS TASK MUST BE RAN AFTER RENAMING
            if (!cancelled) {

                Task deleteOldBackupTask = new DeleteOldBackupsTask(false, permissions, sender);
                tasks.add(deleteOldBackupTask);
                deleteOldBackupTask.run();

                if (Config.getInstance().isDeleteBrokenBackups()) {
                    Task deleteBrokeBackupsTask = new DeleteBrokenBackupsTask(false, permissions, sender);
                    tasks.add(deleteBrokeBackupsTask);
                    deleteBrokeBackupsTask.run();
                }
            }

            if (setLocked) {
                if (isAutoBackup) {
                    Logger.getLogger().log("Auto backup task completed", sender);
                }
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

            if (!cancelled) {
                if (afterBackup.equals("RESTART")) {

                    Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {
                        Scheduler.cancelTasks(Utils.plugin);
                        Bukkit.getServer().spigot().restart();
                    }, 20);

                } else if (afterBackup.equals("STOP")) {

                    Logger.getLogger().log("Stopping server...", sender);
                    Bukkit.shutdown();
                }
            }

            Logger.getLogger().devLog("Backup task has been finished");

        } catch (Exception e) {

            Logger.getLogger().warn("Something went wrong while running the task: " + taskName);
            Logger.getLogger().warn(e.getMessage());

            if (isAutoBackup) {
                Logger.getLogger().log("Auto backup task has been finished with an exception", sender);
            }

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

            this.backupName = LocalDateTime.now().format(Config.getInstance().getDateTimeFormatter()) + " in progress";

            if (!cancelled && isLocal) {
                prepareLocalTask();
            }
            if (!cancelled && isFtp) {
                prepareFtpTask();
            }
            if (!cancelled && isSftp) {
                prepareSftpTask();
            }
            if (!cancelled && isGoogleDrive) {
                prepareGoogleDriveTask();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
            }

            new SetWorldsWritableTask(false, permissions, sender).run();

            Logger.getLogger().warn("The Backup task has been finished with an exception!", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    private void prepareLocalTask() {

        try {

            if (cancelled) {
                return;
            }

            File backupDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(Config.getInstance().getLocalConfig().isZipArchive() ? backupName + ".zip" :  backupName).toFile();

            if (!Config.getInstance().getLocalConfig().isZipArchive() && !backupDir.mkdir()) {

                Logger.getLogger().warn("Can not create " + backupDir.getPath() + " dir!", sender);
            }

            ArrayList<File> dirsToAddToZip = new ArrayList<>();

            for (String directoryToBackup : getDirectoriesToBackup()) {

                try {

                    if (cancelled) {
                        break;
                    }

                    File additionalDirectoryToBackupFile = Paths.get(directoryToBackup).toFile();
                    boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);

                    if (!additionalDirectoryToBackupFile.exists()) {
                        Logger.getLogger().warn("addDirectoryToBackup \"" + additionalDirectoryToBackupFile.getPath() + "\" does not exist!");
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    if (!Config.getInstance().getLocalConfig().isZipArchive()) {

                        Task task = new CopyFilesToFolderTask(additionalDirectoryToBackupFile, backupDir, true, false, false, permissions, sender);
                        task.prepareTask();

                        tasks.add(task);

                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile);
                    }
                } catch (Exception e) {
                    Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + directoryToBackup + "\"", sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }
            }

            if (Config.getInstance().getLocalConfig().isZipArchive()) {

                Task task = new AddDirToZipTask(dirsToAddToZip, backupDir, true, false, false, permissions, sender);
                task.prepareTask();

                tasks.add(task);
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to prepare local backup task");
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    private void prepareSftpTask() {

        try {

            if (cancelled) {
                return;
            }

            SftpUtils.createFolder(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), backupName), sender);


            for (String directoryToBackup : getDirectoriesToBackup()) {

                try {

                    if (cancelled) {
                        break;
                    }

                    File additionalDirectoryToBackupFile = Paths.get(directoryToBackup).toFile();
                    boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);

                    if (!additionalDirectoryToBackupFile.exists()) {
                        Logger.getLogger().warn("addDirectoryToBackup \"" + additionalDirectoryToBackupFile.getPath() + "\" does not exist!");
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    Task task = new SftpSendFileFolderTask(additionalDirectoryToBackupFile, SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                            backupName), true, false, false, permissions, sender);
                    task.prepareTask();

                    tasks.add(task);

                } catch (Exception e) {
                    Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + directoryToBackup + "\"", sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to prepare sftp backup task");
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    private void prepareFtpTask() {

        try {

            if (cancelled) {
                return;
            }

            if (!Config.getInstance().getFtpConfig().isZipArchive()) {
                FtpUtils.createFolder(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), backupName), sender);
            }

            ArrayList<File> dirsToAddToZip = new ArrayList<>();

            for (String directoryToBackup : getDirectoriesToBackup()) {

                try {

                    if (cancelled) {
                        break;
                    }

                    File additionalDirectoryToBackupFile = Paths.get(directoryToBackup).toFile();
                    boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);

                    if (!additionalDirectoryToBackupFile.exists()) {
                        Logger.getLogger().warn("addDirectoryToBackup \"" + additionalDirectoryToBackupFile.getPath() + "\" does not exist!");
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    if (!Config.getInstance().getFtpConfig().isZipArchive()) {

                        Task task = new FtpSendFileFolderTask(additionalDirectoryToBackupFile, FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(),
                                backupName), true, false, false, permissions, sender);
                        task.prepareTask();

                        tasks.add(task);
                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile);
                    }

                } catch (Exception e) {
                    Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + directoryToBackup + "\"", sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }
            }

            if (Config.getInstance().getFtpConfig().isZipArchive()) {

                String targetZipPath = FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), backupName + ".zip");
                Task task = new FtpAddLocalDirsToZipTask(dirsToAddToZip, targetZipPath, true, false, false, permissions, sender);
                task.prepareTask();

                tasks.add(task);
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to prepare FTP(S) backup task");
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    public void prepareGoogleDriveTask() {

        try {

            if (cancelled) {
                return;
            }

            String backupDriveFileId = GoogleDriveUtils.createFolder(backupName, Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), sender);


            for (String directoryToBackup : getDirectoriesToBackup()) {

                try {

                    if (cancelled) {
                        break;
                    }

                    File additionalDirectoryToBackupFile = Paths.get(directoryToBackup).toFile();
                    boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);

                    if (!additionalDirectoryToBackupFile.exists()) {
                        Logger.getLogger().warn("addDirectoryToBackup \"" + additionalDirectoryToBackupFile.getPath() + "\" does not exist!");
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    Task task = new GoogleDriveSendFileFolderTask(additionalDirectoryToBackupFile, backupDriveFileId, additionalDirectoryToBackupFile.getName(),
                            true, false, false, permissions, sender);
                    task.prepareTask();

                    tasks.add(task);

                } catch (Exception e) {
                    Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + directoryToBackup + "\"", sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to prepare Google Drive backup task");
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return getTaskMaxProgress();
        }

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
            if (task instanceof SftpSendFileFolderTask) {
                taskProgressMultiplier = sendSftpProgressMultiplier;
            }
            if (task instanceof FtpSendFileFolderTask) {
                taskProgressMultiplier = sendFtpProgressMultiplier;
            }
            if (task instanceof FtpAddLocalDirsToZipTask) {
                taskProgressMultiplier = zipFtpProgressMultiplier;
            }
            if (task instanceof GoogleDriveSendFileFolderTask) {
                taskProgressMultiplier = sendGoogleDriveProgressMultiplier;
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
            if (task instanceof SftpSendFileFolderTask) {
                taskProgressMultiplier = sendSftpProgressMultiplier;
            }
            if (task instanceof FtpSendFileFolderTask) {
                taskProgressMultiplier = sendFtpProgressMultiplier;
            }
            if (task instanceof FtpAddLocalDirsToZipTask) {
                taskProgressMultiplier = zipFtpProgressMultiplier;
            }
            if (task instanceof GoogleDriveSendFileFolderTask) {
                taskProgressMultiplier = sendGoogleDriveProgressMultiplier;
            }

            maxProgress += maxTaskProgress * taskProgressMultiplier;
        }

        return maxProgress;
    }

    @Override
    public void cancel() {
        cancelled = true;

        for (Task task : tasks) {
            task.cancel();
        }
    }

    /**
     * Return all the directories to backup (worlds + add directories).
     * It might contain directory that does not exist or that need to be excluded
     */
    private List<String> getDirectoriesToBackup(){
        // return the 2 lists (worlds & add directories) merged and without duplicates
        List<String> list = Stream.concat(getWorldsDirectoryToBackup().stream(), getAddDirectoryToBackup().stream()).distinct().toList();
        return list;
    }

    private List<String> getWorldsDirectoryToBackup() {
        // Get the path of all worlds as a list of strings
        List<String> list = Bukkit.getWorlds().stream().map(world -> world.getWorldFolder().getPath()).toList();
        return list;
    }

    private List<String> getAddDirectoryToBackup() {
        // if contains "*" add all Files from "."
        if(Config.getInstance().getAddDirectoryToBackup().contains("*")){
            // Remove the "*" from the list
            List<String> list = Config.getInstance().getAddDirectoryToBackup().stream().filter(directory -> !directory.equals("*")).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            // Add all files from "."
            File file = new File(".");
            for (File subFile: file.listFiles()) {
                list.add(subFile.getPath());
            }
            return list;
        }else{
            List<String> list = Config.getInstance().getAddDirectoryToBackup().stream().map(addDirectory -> new File(addDirectory).getPath()).toList();
            return list;
        }
    }
}
