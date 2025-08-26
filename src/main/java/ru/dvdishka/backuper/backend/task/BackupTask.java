package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class BackupTask extends BaseTask {

    private final boolean isAutoBackup;
    private final String afterBackup;
    private final List<Storage> storages;

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

    private final List<Task> tasks = new ArrayList<>();

    public BackupTask(List<Storage> storages, String afterBackup, boolean isAutoBackup) {
        super();
        this.storages = storages;
        this.afterBackup = afterBackup.toUpperCase();
        this.isAutoBackup = isAutoBackup;
    }

    @Override
    @ApiStatus.Internal
    public void start(CommandSender sender) throws TaskException {
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

    @Override
    public void run() throws IOException {

        HashMap<Storage, Long> storageBackupByteSize = new HashMap<>();
        List<CompletableFuture<Void>> taskFutures = new ArrayList<>();

        // Lock world folders if necessary
        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(new SetWorldsReadOnlyTask(), sender);
            } catch (TaskException e) {
                warn(e);
            }
        }
        for (Task task : tasks) {

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
            if (!cancelled && (task instanceof UploadDirTask uploadDirTask)) {
                storageBackupByteSize.compute(uploadDirTask.getStorage(), (storage, size) -> size == null ? uploadDirTask.getTaskMaxProgress() : size + uploadDirTask.getTaskMaxProgress());
            }
        }

        CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join(); // Waiting for all tasks to be completed
        try {
            Backuper.getInstance().getTaskManager().startTaskRaw(new SetWorldsWritableTask(), sender); // We should unlock folders even if they weren't locked
        } catch (TaskException e) {
            warn(e);
        }

        for (Storage storage : storages) {
            // RENAME TASK
            if (!cancelled) {

                devLog("The Rename \"in progress\" Folder %s storage task has been started".formatted(storage.getId()));
                String fileType = "";
                if (storage.getConfig().isZipArchive()) {
                    fileType = ".zip";
                }

                try {
                    storage.renameFile(storage.resolve(storage.getConfig().getBackupsFolder(), backupName + fileType), backupName.replace(" in progress", "") + fileType);

                    // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED). NECESSARY TO DO AFTER RENAMING
                    if (!storage.getConfig().isZipArchive()) {
                        Backup.saveBackupSizeToCache(storage, backupName.replace(" in progress", ""), storageBackupByteSize.get(storage));
                        devLog("New GOOGLE_DRIVE backup size has been cached");
                    }
                } catch (Exception e) {
                    warn("Failed to rename Google Drive file %s".formatted(backupName), sender);
                    warn(e);
                }
                devLog("The Rename \"in progress\" Folder %s storage task has been finished".formatted(storage.getId()));
            }
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

            for (Storage storage : storages) {
                if (!cancelled) {
                    prepareStorageTask(storage);
                }
            }

        } catch (Exception e) {
            warn("The Backup task has been finished with an exception!", this.sender);
            warn(e);
        }
    }

    private void prepareStorageTask(Storage storage) {

        try {
            if (cancelled) {
                return;
            }

            if (!storage.getConfig().isZipArchive()) {
                storage.createDir(backupName, storage.getConfig().getBackupsFolder());
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
                        AsyncTask task = new UploadDirTask(storage, additionalDirectoryToBackupFile, storage.resolve(storage.getConfig().getBackupsFolder(),
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

            if (storage.getConfig().isZipArchive()) {
                AsyncTask task = new UploadDirsAsZip(storage, dirsToAddToZip, storage.getConfig().getBackupsFolder(), "%s.zip".formatted(backupName), true, false);
                Backuper.getInstance().getTaskManager().prepareTask(task, sender);

                tasks.add(task);
            }

        } catch (Exception e) {
            warn("Something went wrong while trying to prepare %s storage backup task".formatted(storage.getId()));
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
    public void cancel() {
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
}
