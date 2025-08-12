package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.FtpUtils;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.util.SftpUtils;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BackupTask extends BaseAsyncTask {

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
     * May contain the "in progress" part
     **/
    private String backupName;

    private final List<BaseTask> tasks = new ArrayList<>();

    /**
     * Storage will be used if it is enabled and autoBackup for this storage is enabled too
     **/
    public BackupTask(String afterBackup, boolean isAutoBackup) {

        super();
        this.afterBackup = afterBackup.toUpperCase();
        this.isAutoBackup = isAutoBackup;
        this.isLocal = Config.getInstance().getLocalConfig().isAutoBackup() && Config.getInstance().getLocalConfig().isEnabled();
        this.isFtp = Config.getInstance().getFtpConfig().isAutoBackup() && Config.getInstance().getFtpConfig().isEnabled();
        this.isSftp = Config.getInstance().getSftpConfig().isAutoBackup() && Config.getInstance().getSftpConfig().isEnabled();
        this.isGoogleDrive = Config.getInstance().getGoogleDriveConfig().isAutoBackup() && Config.getInstance().getGoogleDriveConfig().isEnabled();
    }

    public BackupTask(String afterBackup, boolean isAutoBackup, boolean isLocal, boolean isFtp, boolean isSftp, boolean isGoogleDrive) {
        super();
        this.afterBackup = afterBackup.toUpperCase();
        this.isAutoBackup = isAutoBackup;
        this.isLocal = isLocal;
        this.isFtp = isFtp;
        this.isSftp = isSftp;
        this.isGoogleDrive = isGoogleDrive;
    }

    @Override
    @ApiStatus.Internal
    protected void start(CommandSender sender) throws TaskException {
        try {
            if (!cancelled) {
                this.sender = sender;
            }
            //Check if this backup should be skipped before precalculations
            if (!cancelled && Config.getInstance().isSkipDuplicateBackup() && isAutoBackup &&
                    Config.getInstance().getLastBackup() >= Config.getInstance().getLastChange()) {

                log("The backup cycle will be skipped since there were no changes from the previous backup", sender);
                Config.getInstance().updateLastBackup();

                if (afterBackup.equals("RESTART")) {

                    Backuper.getInstance().getScheduleManager().runSyncDelayed(Backuper.getInstance(), () -> {
                        Backuper.getInstance().getScheduleManager().destroy(Backuper.getInstance());
                        Bukkit.getServer().restart();
                    }, 20);

                } else if (afterBackup.equals("STOP")) {

                    devLog("Stopping server...");
                    Bukkit.shutdown();
                }
                return;
            }

            if (!isTaskPrepared() && !cancelled) {
                Backuper.getInstance().getTaskManager().prepareTask(this, sender);
            }
            if (!cancelled) {
                prepareTaskFuture.get();
            }
            if (!cancelled) {
                run();
            }
        } catch (Exception e) {
            throw new TaskException(this, e);
        }
    }

    private Backup.StorageType getStorageTypeFromTask(BaseTask task) {
        return switch (task) {
            case FtpAddLocalDirToZipTask ftpTask -> Backup.StorageType.FTP;
            case FtpSendDirTask ftpTask -> Backup.StorageType.FTP;
            case SftpAddLocalDirToZipTask sftpTask -> Backup.StorageType.SFTP;
            case SftpSendDirTask sftpTask -> Backup.StorageType.SFTP;
            case GoogleDriveAddLocalDirToZip googleDriveTask -> Backup.StorageType.GOOGLE_DRIVE;
            case GoogleDriveSendDirTask googleDriveTask -> Backup.StorageType.GOOGLE_DRIVE;
            case CopyDirTask copyTask -> Backup.StorageType.LOCAL;
            case AddLocalDirToZipTask addLocalDirToZipTask -> Backup.StorageType.LOCAL;
            default -> Backup.StorageType.NULL;
        };
    }

    @Override
    protected void run() throws IOException {

        try {
            if (!cancelled && isFtp && !FtpUtils.checkConnection()) {
                warn("Failed to connect to FTP(S) server during the backup task. Skipping this storage...", sender);
                isFtp = false;
            }
        } catch (Exception e) {
            warn("Failed to connect to FTP(S) server during the backup task. Skipping this storage...", sender);
            warn(e);
            isFtp = false;
        }

        try {
            if (!cancelled && isSftp && !SftpUtils.checkConnection()) {
                warn("Failed to connect to SFTP server during the backup task. Skipping this storage...", sender);
                isSftp = false;
            }
        } catch (Exception e) {
            warn("Failed to connect to SFTP server during the backup task. Skipping this storage...", sender);
            warn(e);
            isSftp = false;
        }

        try {
            if (!cancelled && isGoogleDrive && !GoogleDriveUtils.checkConnection()) {
                warn("Failed to connect to Google Drive or Google account is not linked!", sender);
                isGoogleDrive = false;
            }
        } catch (Exception e) {
            warn("Failed to connect to Google Drive or Google account is not linked!", sender);
            warn(e);
            isGoogleDrive = false;
        }

        long localBackupByteSize = 0, ftpBackupByteSize = 0, sftpBackupByteSize = 0, googleDriveBackupByteSize = 0;

        List<CompletableFuture<Void>> taskFutures = new ArrayList<>();

        // Lock world folders if it is necessary
        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(new SetWorldsReadOnlyTask(), sender);
            } catch (TaskException e) {
                warn(e);
            }
        }
        for (BaseTask task : tasks) {
            // Check if storage is enabled
            if (Backup.StorageType.FTP.equals(getStorageTypeFromTask(task)) && !isFtp) {
                continue;
            }
            if (Backup.StorageType.SFTP.equals(getStorageTypeFromTask(task)) && !isSftp) {
                continue;
            }
            if (Backup.StorageType.GOOGLE_DRIVE.equals(getStorageTypeFromTask(task)) && !isGoogleDrive) {
                continue;
            }
            if (Backup.StorageType.LOCAL.equals(getStorageTypeFromTask(task)) && !isLocal) {
                continue;
            }

            // Start task paralleled
            if (!cancelled) {
                taskFutures.add(Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try {
                        Backuper.getInstance().getTaskManager().startTaskRaw(task, sender);
                    } catch (TaskException e) {
                        warn(e);
                    }
                }));
            }

            // Calculate a new backup size
            if (!cancelled && (task instanceof CopyDirTask)) {
                localBackupByteSize += task.getTaskMaxProgress();
            }
            if (!cancelled && (task instanceof FtpSendDirTask)) {
                ftpBackupByteSize += task.getTaskMaxProgress();
            }
            if (!cancelled && (task instanceof SftpSendDirTask)) {
                sftpBackupByteSize += task.getTaskMaxProgress();
            }
            if (!cancelled && (task instanceof GoogleDriveSendDirTask)) {
                googleDriveBackupByteSize += task.getTaskMaxProgress();
            }
        }

        CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join(); // Waiting for all tasks to be completed
        try {
            Backuper.getInstance().getTaskManager().startTaskRaw(new SetWorldsWritableTask(), sender); // We should unlock folders even if they weren't locked
        } catch (TaskException e) {
            warn(e);
        }

        if (!cancelled && isLocal) {

            File backupDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(Config.getInstance().getLocalConfig().isZipArchive() ? "%s.zip".formatted(backupName) :  backupName).toFile();

            // RENAME LOCAL TASK
            {

                devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
                File oldZipFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile();
                File newZipFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(backupDir.getName().replace(" in progress", "")).toFile();

                try {
                    int attempts = 0;
                    while (!oldZipFile.renameTo(newZipFile) && attempts < 1000000) {
                        if (attempts == 999999) {
                            warn("The Rename \"in progress\" local task has been finished with an exception!", sender);
                        }
                        attempts++;
                    }

                    if (!Config.getInstance().getLocalConfig().isZipArchive()) {
                        // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED)
                        Backup.saveBackupSizeToCache(Backup.StorageType.LOCAL, backupName.replace(" in progress", ""), localBackupByteSize);
                        devLog("New LOCAL backup size has been cached");
                    }
                } catch (Exception e) {
                    warn("Failed to rename local file %s".formatted(backupName), sender);
                    warn(e);
                }

                devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
            }
        }

        // RENAME FTP TASK
        if (!cancelled && isFtp) {

            devLog("The Rename \"in progress\" Folder FTP(S) task has been started");

            String fileType = "";

            if (Config.getInstance().getFtpConfig().isZipArchive()) {
                fileType = ".zip";
            }

            try {
                FtpUtils.renameFile(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(),
                        backupName + fileType), FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(),
                        backupName.replace(" in progress", "") + fileType));

                if (!Config.getInstance().getFtpConfig().isZipArchive()) {
                    // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED)
                    Backup.saveBackupSizeToCache(Backup.StorageType.FTP, backupName.replace(" in progress", ""), ftpBackupByteSize);
                    devLog("New SFTP backup size has been cached");
                }
            } catch (Exception e) {
                warn("Failed to rename FTP(S) file %s".formatted(backupName), sender);
                warn(e);
            }

            devLog("The Rename \"in progress\" Folder FTP(S) task has been finished");
        }

        // RENAME SFTP TASK
        if (!cancelled && isSftp) {

            devLog("The Rename \"in progress\" Folder SFTP task has been started");

            String fileType = "";

            if (Config.getInstance().getSftpConfig().isZipArchive()) {
                fileType = ".zip";
            }

            try {
                SftpUtils.renameFile(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                        backupName + fileType), SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                        backupName.replace(" in progress", "") + fileType));

                // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED)
                if (!Config.getInstance().getSftpConfig().isZipArchive()) {
                    Backup.saveBackupSizeToCache(Backup.StorageType.SFTP, backupName.replace(" in progress", ""), sftpBackupByteSize);
                    devLog("New SFTP backup size has been cached");
                }
            } catch (Exception e) {
                warn("Failed to rename SFTP file %s".formatted(backupName), sender);
                warn(e);
            }

            devLog("The Rename \"in progress\" Folder SFTP task has been finished");
        }

        // RENAME GOOGLE DRIVE TASK
        if (!cancelled && isGoogleDrive) {

            devLog("The Rename \"in progress\" Folder GoogleDrive task has been started");

            String fileType = "";

            if (Config.getInstance().getGoogleDriveConfig().isZipArchive()) {
                fileType = ".zip";
            }

            try {
                GoogleDriveUtils.renameFile(GoogleDriveUtils.getFileByName(backupName + fileType, Config.getInstance().getGoogleDriveConfig().getBackupsFolderId()).getId(), backupName.replace(" in progress", "") + fileType);

                // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED). NECESSARY TO DO AFTER RENAMING
                if (!Config.getInstance().getGoogleDriveConfig().isZipArchive()) {
                    Backup.saveBackupSizeToCache(Backup.StorageType.GOOGLE_DRIVE, backupName.replace(" in progress", ""), googleDriveBackupByteSize);
                    devLog("New GOOGLE_DRIVE backup size has been cached");
                }
            } catch (Exception e) {
                warn("Failed to rename Google Drive file %s".formatted(backupName), sender);
                warn(e);
            }

            devLog("The Rename \"in progress\" Folder GoogleDrive task has been finished");
        }

        // UPDATE VARIABLES
        if (!cancelled && isAutoBackup) {
            devLog("Update \"lastBackup\" Variable task has been started");
            Config.getInstance().updateLastBackup();
            devLog("Update \"lastBackup\" Variable task has been finished");
        }

        // DELETE OLD BACKUPS TASK MUST BE RUN AFTER RENAMING
        if (!cancelled) {

            BaseTask deleteOldBackupTask = new DeleteOldBackupsTask();
            tasks.add(deleteOldBackupTask);
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(deleteOldBackupTask, sender);
            } catch (Exception e) {
                warn(new TaskException(deleteOldBackupTask, e));
            }

            if (Config.getInstance().isDeleteBrokenBackups()) {
                BaseTask deleteBrokenBackupsTask = new DeleteBrokenBackupsTask();
                tasks.add(deleteBrokenBackupsTask);
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(deleteBrokenBackupsTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(deleteBrokenBackupsTask, e));
                }
            }
        }

        if (!cancelled) {
            if (afterBackup.equals("RESTART")) {

                Backuper.getInstance().getScheduleManager().runSyncDelayed(Backuper.getInstance(), () -> {
                    Backuper.getInstance().getScheduleManager().destroy(Backuper.getInstance());
                    Bukkit.getServer().restart();
                }, 20);

            } else if (afterBackup.equals("STOP")) {

                log("Stopping server...", sender);
                Bukkit.shutdown();
            }
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {

        try {
            this.backupName = "%s in progress".formatted(LocalDateTime.now().format(Config.getInstance().getDateTimeFormatter()));

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
            warn("The Backup task has been finished with an exception!", this.sender);
            warn(e);
        }
    }

    private void prepareLocalTask() {

        try {

            if (cancelled) {
                return;
            }

            File backupDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath().resolve(Config.getInstance().getLocalConfig().isZipArchive() ? "%s.zip".formatted(backupName) :  backupName).toFile();

            if (!Config.getInstance().getLocalConfig().isZipArchive() && !backupDir.mkdir()) {

                warn("Can not create %s dir!".formatted(backupDir.getPath()), sender);
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
                        warn("addDirectoryToBackup \"%s\" does not exist!".formatted(additionalDirectoryToBackupFile.getPath()));
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    if (!Config.getInstance().getLocalConfig().isZipArchive()) {

                        BaseTask task = new CopyDirTask(additionalDirectoryToBackupFile, backupDir, true, false);
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                        tasks.add(task);

                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile);
                    }
                } catch (Exception e) {
                    warn("Something went wrong when trying to backup an additional directory \"%s\"".formatted(directoryToBackup), sender);
                    warn(e);
                }
            }

            if (Config.getInstance().getLocalConfig().isZipArchive()) {

                BaseTask task = new AddLocalDirToZipTask(dirsToAddToZip, backupDir, true, false);
                Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                tasks.add(task);
            }

        } catch (Exception e) {
            warn("Something went wrong while trying to prepare local backup task");
            warn(e);
        }
    }

    private void prepareSftpTask() {

        try {

            if (cancelled) {
                return;
            }

            if (!Config.getInstance().getSftpConfig().isZipArchive()) {
                SftpUtils.createFolder(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), backupName));
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
                        warn("addDirectoryToBackup \"%s\" does not exist!".formatted(additionalDirectoryToBackupFile.getPath()));
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    if (!Config.getInstance().getSftpConfig().isZipArchive()) {
                        BaseTask task = new SftpSendDirTask(additionalDirectoryToBackupFile, SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(),
                                backupName), true, false);
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                        tasks.add(task);
                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile);
                    }

                } catch (Exception e) {
                    warn("Something went wrong when trying to backup an additional directory \"%s\"".formatted(directoryToBackup), sender);
                    warn(e);
                }
            }

            if (Config.getInstance().getFtpConfig().isZipArchive()) {

                String targetZipPath = SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), "%s.zip".formatted(backupName));
                BaseTask task = new SftpAddLocalDirToZipTask(dirsToAddToZip, targetZipPath, true, false);
                Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                tasks.add(task);
            }

        } catch (Exception e) {
            warn("Something went wrong while trying to prepare sftp backup task");
            warn(e);
        }
    }

    private void prepareFtpTask() {

        try {

            if (cancelled) {
                return;
            }

            if (!Config.getInstance().getFtpConfig().isZipArchive()) {
                FtpUtils.createFolder(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), backupName));
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
                        warn("addDirectoryToBackup \"%s\" does not exist!".formatted(additionalDirectoryToBackupFile.getPath()));
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    if (!Config.getInstance().getFtpConfig().isZipArchive()) {

                        BaseTask task = new FtpSendDirTask(additionalDirectoryToBackupFile, FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(),
                                backupName), true, false);
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                        tasks.add(task);
                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile);
                    }

                } catch (Exception e) {
                    warn("Something went wrong when trying to backup an additional directory \"%s\"".formatted(directoryToBackup), sender);
                    warn(e);
                }
            }

            if (Config.getInstance().getFtpConfig().isZipArchive()) {

                String targetZipPath = FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), "%s.zip".formatted(backupName));
                BaseTask task = new FtpAddLocalDirToZipTask(dirsToAddToZip, targetZipPath, true, false);
                Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                tasks.add(task);
            }

        } catch (Exception e) {
            warn("Something went wrong while trying to prepare FTP(S) backup task");
            warn(e);
        }
    }

    private void prepareGoogleDriveTask() {

        try {

            if (cancelled) {
                return;
            }

            String backupDriveFileId = null;
            if (!Config.getInstance().getGoogleDriveConfig().isZipArchive()) {
                backupDriveFileId = GoogleDriveUtils.createFolder(backupName, Config.getInstance().getGoogleDriveConfig().getBackupsFolderId());
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
                        warn("addDirectoryToBackup \"%s\" does not exist!".formatted(additionalDirectoryToBackupFile.getPath()));
                        continue;
                    }

                    if (isExcludedDirectory) {
                        continue;
                    }

                    if (!Config.getInstance().getGoogleDriveConfig().isZipArchive()) {

                        BaseTask task = new GoogleDriveSendDirTask(additionalDirectoryToBackupFile, backupDriveFileId, additionalDirectoryToBackupFile.getName(),
                                true, false);
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                        tasks.add(task);
                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile);
                    }

                } catch (Exception e) {
                    warn("Something went wrong when trying to backup an additional directory \"%s\"".formatted(directoryToBackup), sender);
                    warn(e);
                }
            }

            if (Config.getInstance().getGoogleDriveConfig().isZipArchive()) {
                BaseTask task = new GoogleDriveAddLocalDirToZip(dirsToAddToZip, Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(),
                        "%s.zip".formatted(backupName), true, false);
                Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                tasks.add(task);
            }

        } catch (Exception e) {
            warn("Something went wrong while trying to prepare Google Drive backup task");
            warn(e);
        }
    }

    private long getTaskProgressMultiplier(BaseTask task) {
        return switch (task) {
            case DeleteOldBackupsTask deleteOldBackupsTask -> deleteProgressMultiplier;
            case CopyDirTask copyDirTask -> copyProgressMultiplier;
            case AddLocalDirToZipTask addLocalDirToZipTask -> zipProgressMultiplier;
            case SftpSendDirTask sftpSendDirTask -> sendSftpProgressMultiplier;
            case FtpSendDirTask ftpSendDirTask -> sendFtpProgressMultiplier;
            case FtpAddLocalDirToZipTask ftpAddLocalDirToZipTask -> zipFtpProgressMultiplier;
            case GoogleDriveSendDirTask googleDriveSendDirTask -> sendGoogleDriveProgressMultiplier;
            case null, default -> 1;
        };
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return getTaskMaxProgress();
        }

        long currentProgress = 0;

        for (BaseTask task : tasks) {
            long currentTaskProgress = task.getTaskCurrentProgress();
            currentProgress += currentTaskProgress * getTaskProgressMultiplier(task);
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {

        long maxProgress = 0;

        for (BaseTask task : tasks) {
            long maxTaskProgress = task.getTaskMaxProgress();
            maxProgress += maxTaskProgress * getTaskProgressMultiplier(task);
        }

        return maxProgress;
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (BaseTask task : tasks) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
        }
    }

    /**
     * Return all the directories to backup (worlds + add directories).
     * It might contain a directory that does not exist or that needs to be excluded
     */
    private List<String> getDirectoriesToBackup(){
        // return the 2 lists (worlds & add directories) merged and without duplicates
        return Stream.concat(getWorldsDirectoryToBackup().stream(), getAddDirectoryToBackup().stream()).distinct().toList();
    }

    private List<String> getWorldsDirectoryToBackup() {
        // Get the path of all worlds as a list of strings
        return Bukkit.getWorlds().stream().map(world -> world.getWorldFolder().getPath()).toList();
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
            return Config.getInstance().getAddDirectoryToBackup().stream().map(addDirectory -> new File(addDirectory).getPath()).toList();
        }
    }

    public boolean isLocal() {
        return isLocal;
    }

    public boolean isSftp() {
        return isSftp;
    }

    public boolean isFtp() {
        return isFtp;
    }

    public boolean isGoogleDrive() {
        return isGoogleDrive;
    }
}
