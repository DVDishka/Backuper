package ru.dvdishka.backuper.backend.task;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.lang.Long.max;

public class BackupTask extends BaseTask {

    private final boolean isAutoBackup;
    private final String afterBackup;
    private final List<Storage> storages;

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
            if (!cancelled && Backuper.getInstance().getConfigManager().getBackupConfig().isSkipDuplicateBackup() && isAutoBackup &&
                    Backuper.getInstance().getConfigManager().getLastBackup() >= Backuper.getInstance().getConfigManager().getLastChange()) {

                log("The backup cycle will be skipped since there were no changes from the previous backup", sender);

                if (afterBackup.equals("RESTART")) {
                    Backuper.getInstance().getScheduleManager().runGlobalRegionDelayed(Backuper.getInstance(), () -> {
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
                try {
                    Backuper.getInstance().getTaskManager().prepareTask(this, sender);
                } catch (Throwable e) {
                    throw new TaskException(this, e);
                }
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
    public void run() {

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
        HashMap<Storage, List<Task>> storageTasks = new HashMap<>();
        for (Task task : tasks) {
            if (task instanceof DoubleStorageTask doubleStorageTask) {
                storageTasks.compute(doubleStorageTask.getTargetStorage(), (storage, tasks) -> {
                    if (tasks == null) tasks = new ArrayList<>();
                    tasks.add(doubleStorageTask);
                    return tasks;
                });
            } else {
                Backuper.getInstance().getLogManager().warn("Non-DoubleStorageTask found in BackupTask tasks list: %s".formatted(task.getClass().getName()));
            }
        }

        for (Storage storage : storageTasks.keySet()) {
            if (cancelled) break;

            taskFutures.add(Backuper.getInstance().getScheduleManager().runAsync(() -> { // One thread for one storage
                for (Task task : storageTasks.get(storage)) {
                    if (cancelled) break;
                    try {
                        Backuper.getInstance().getTaskManager().startTaskRaw(task, sender);
                    } catch (TaskException e) {
                        warn(e);
                    }

                    // Calculate a new backup size
                    if (!cancelled && (task instanceof TransferDirTask transferDirTask)) {
                        storageBackupByteSize.compute(transferDirTask.getTargetStorage(), (transferTaskStorage, size) -> size == null ? transferDirTask.getTaskMaxProgress() : size + transferDirTask.getTaskMaxProgress()); // There might be several tasks for one storage
                    }
                }
            }));
        }

        CompletableFuture.allOf(taskFutures.toArray(new CompletableFuture[0])).join(); // Waiting for all tasks to be completed
        try {
            Backuper.getInstance().getTaskManager().startTaskRaw(new SetWorldsWritableTask(), sender); // We should unlock folders even if they weren't locked
        } catch (TaskException e) {
            warn(e);
        }

        for (Storage storage : storages) {
            // RENAME TASK
            if (cancelled) break;

            devLog("The Rename \"in progress\" in %s storage task has been started".formatted(storage.getId()));
            String fileType = "";
            if (storage.getConfig().isZipArchive()) {
                fileType = ".zip";
            }

            try {
                storage.renameFile(storage.resolve(storage.getConfig().getBackupsFolder(), backupName + fileType), backupName.replace(" in progress", "") + fileType);

                // Add new backup size to cache (ONLY IF NOT ZIP. ZIP SIZE IS NOT COUNTED). MUST ONLY BE EXECUTED AFTER RENAMING
                if (!storage.getConfig().isZipArchive()) {
                    storage.getBackupManager().saveBackupSizeToCache(backupName.replace(" in progress", ""), storageBackupByteSize.get(storage));
                    devLog("New backup size in %s storage has been cached".formatted(storage.getId()));
                }
            } catch (Exception e) {
                warn("Failed to rename file %s in %s storage".formatted(backupName, storage.getId()), sender);
                warn(e);
            }
            devLog("The Rename \"in progress\" Folder %s storage task has been finished".formatted(storage.getId()));
        }

        // UPDATE VARIABLES
        if (!cancelled && isAutoBackup) {
            devLog("Update \"lastBackup\" Variable task has been started");
            Backuper.getInstance().getConfigManager().updateLastBackup();
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

            if (Backuper.getInstance().getConfigManager().getBackupConfig().isDeleteBrokenBackups()) {
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
                Backuper.getInstance().getScheduleManager().runGlobalRegionDelayed(Backuper.getInstance(), () -> {
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
    public void prepareTask(CommandSender sender) {
        try {
            this.backupName = "%s in progress".formatted(LocalDateTime.now().format(Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter()));
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
            if (cancelled) return;

            if (!storage.getConfig().isZipArchive()) {
                storage.createDir(backupName, storage.getConfig().getBackupsFolder());
            }

            ArrayList<String> dirsToAddToZip = new ArrayList<>();
            for (String directoryToBackup : getDirectoriesToBackup()) {
                try {
                    if (cancelled) return;

                    File additionalDirectoryToBackupFile = Paths.get(directoryToBackup).toFile();
                    boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);
                    if (!additionalDirectoryToBackupFile.exists()) {
                        warn("addDirectoryToBackup \"%s\" does not exist!".formatted(additionalDirectoryToBackupFile.getPath()));
                        continue;
                    }
                    if (isExcludedDirectory) continue;

                    if (!storage.getConfig().isZipArchive()) {
                        Task task = new TransferDirTask(Backuper.getInstance().getStorageManager().getStorage("backuper"), additionalDirectoryToBackupFile.toPath().toAbsolutePath().normalize().toString(), storage, storage.resolve(storage.getConfig().getBackupsFolder(),
                                backupName), true, false);
                        try {
                            Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                        } catch (Throwable e) {
                            throw new TaskException(task, e);
                        }
                        tasks.add(task);
                    } else {
                        dirsToAddToZip.add(additionalDirectoryToBackupFile.toPath().toAbsolutePath().normalize().toString());
                    }
                } catch (Exception e) {
                    warn("Something went wrong when trying to backup an additional directory \"%s\"".formatted(directoryToBackup), sender);
                    warn(e);
                }
            }

            if (storage.getConfig().isZipArchive()) {
                Task task = new TransferDirsAsZipTask(Backuper.getInstance().getStorageManager().getStorage("backuper"), dirsToAddToZip, storage, storage.getConfig().getBackupsFolder(), "%s.zip".formatted(backupName), true, false);
                try {
                    Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                } catch (Throwable e) {
                    throw new TaskException(task, e);
                }
                tasks.add(task);
            }
        } catch (Exception e) {
            warn("Something went wrong while trying to prepare %s storage backup task".formatted(storage.getId()));
            warn(e);
        }
    }

    private long getTaskProgressMultiplier(Task task) {
        long deleteProgressMultiplier = 3;
        final long transferProgressMultiplier = 5;
        final long zipProgressMultiplier = 10;

        return switch (task) {
            case DeleteOldBackupsTask deleteOldBackupsTask -> deleteProgressMultiplier;
            case TransferDirTask transferDirTask -> transferProgressMultiplier * max(transferDirTask.getTargetStorage().getTransferSpeedMultiplier(), transferDirTask.getSourceStorage().getTransferSpeedMultiplier());
            case TransferDirsAsZipTask addLocalDirToZipTask -> zipProgressMultiplier * max(addLocalDirToZipTask.getTargetStorage().getTransferSpeedMultiplier(), addLocalDirToZipTask.getSourceStorage().getTransferSpeedMultiplier());
            default -> 1;
        };
    }

    @Override
    public long getTaskCurrentProgress() {
        return tasks.stream().mapToLong(task -> task.getTaskCurrentProgress() * getTaskProgressMultiplier(task)).sum();
    }

    @Override
    public long getTaskMaxProgress() {
        return tasks.stream().mapToLong(task -> task.getTaskMaxProgress() * getTaskProgressMultiplier(task)).sum();
    }

    @Override
    public void cancel() {
        cancelled = true;
        for (Task task : tasks) {
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
        if(Backuper.getInstance().getConfigManager().getBackupConfig().getAddDirectoryToBackup().contains("*")){
            // Remove the "*" from the list
            List<String> list = Backuper.getInstance().getConfigManager().getBackupConfig().getAddDirectoryToBackup().stream().filter(directory -> !directory.equals("*")).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            // Add all files from "."
            File file = new File(".");
            for (File subFile: file.listFiles()) {
                list.add(subFile.getPath());
            }
            return list;
        }else{
            return Backuper.getInstance().getConfigManager().getBackupConfig().getAddDirectoryToBackup().stream().map(addDirectory -> new File(addDirectory).getPath()).toList();
        }
    }
}
