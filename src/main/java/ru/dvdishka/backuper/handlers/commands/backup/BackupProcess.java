package ru.dvdishka.backuper.handlers.commands.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Utility;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.classes.Task;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.backend.config.Config;

class BackupProcess implements Runnable, Task {

    private final String afterBackup;
    private final CommandSender sender;
    private final boolean isAutoBackup;
    private volatile long maxProgress = 0;
    private volatile long currentProgress = 0;

    private final String taskName;

    private final long deleteProgressMultiplier = 1;
    private final long copyProgressMultiplier = 5;
    private final long zipProgressMultiplier = 10;

    BackupProcess(String taskName, String afterBackup, boolean isAutoBackup, CommandSender sender) {
        this.taskName = taskName;
        this.afterBackup = afterBackup;
        this.isAutoBackup = isAutoBackup;
        this.sender = sender;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public long getTaskProgress() {
        return (long) (((double) currentProgress) / ((double) maxProgress) * 100.0);
    }

    public void run() {

        try {

            File backupDir = new File("plugins/Backuper/Backups/" +
                    LocalDateTime.now().format(Backup.dateTimeFormatter) + " in progress");
            File backupsDir = new File(Config.getInstance().getBackupsFolder());

            maxProgress = calculateMaxProgress();

            {
                Logger.getLogger().devLog("Copy/Zip task has been started");

                if (!Config.getInstance().isZipArchive() && !backupDir.mkdir()) {

                    Logger.getLogger().warn("Can not create " + backupDir.getPath() + " dir!", sender);
                }

                FileOutputStream fileOutputStream;
                ZipOutputStream zipOutputStream = null;

                if (Config.getInstance().isZipArchive()) {

                    fileOutputStream = new FileOutputStream(backupDir.getPath() + ".zip");
                    zipOutputStream = new ZipOutputStream(fileOutputStream);
                }

                for (World world : Bukkit.getWorlds()) {

                    File worldDir = world.getWorldFolder();


                    if (worldDir.listFiles() != null) {

                        try {

                            if (Config.getInstance().isZipArchive()) {

                                addDirToZip(zipOutputStream, worldDir, worldDir.getParentFile().toPath());

                            } else {

                                runCopyFilesInDir(backupDir.toPath().resolve(world.getName()).toFile(), worldDir);
                            }

                        } catch (Exception e) {

                            Logger.getLogger().warn("Something went wrong when trying to copy files!", sender);
                            Logger.getLogger().warn(this, e);
                        }
                    }
                }

                for (String additionalDirectoryToBackup : Config.getInstance().getAddDirectoryToBackup()) {

                    try {

                        File additionalDirectoryToBackupFile = Paths.get(additionalDirectoryToBackup).toFile();
                        boolean isExcludedDirectory = Utils.isExcludedDirectory(additionalDirectoryToBackupFile, sender);

                        if (isExcludedDirectory) {
                            continue;
                        }

                        if (Config.getInstance().isZipArchive()) {

                            addDirToZip(zipOutputStream, additionalDirectoryToBackupFile, additionalDirectoryToBackupFile.getCanonicalFile().getParentFile().toPath());

                        } else {

                            runCopyFilesInDir(backupDir.toPath().resolve(additionalDirectoryToBackupFile.getName()).toFile(), additionalDirectoryToBackupFile);
                        }

                    } catch (Exception e) {
                        Logger.getLogger().warn("Something went wrong when trying to backup an additional directory \"" + additionalDirectoryToBackup + "\"", sender);
                        Logger.getLogger().warn(this, e);
                    }
                }

                if (Config.getInstance().isZipArchive()) {

                    assert zipOutputStream != null;
                    zipOutputStream.close();
                }
                Logger.getLogger().devLog("Copy/Zip task has been finished");
            }

            {
                Logger.getLogger().devLog("Set writable task has been started");
                BackupProcessStarter.setWorldsWritableSync(sender, false);
                Logger.getLogger().devLog("Set writable task has been finished");
            }

            {
                Logger.getLogger().devLog("Move task has been started");
                if (!new File(Config.getInstance().getBackupsFolder()).getCanonicalFile()
                        .equals(new File("plugins/Backuper/Backups").getCanonicalFile())) {

                    if (Config.getInstance().isZipArchive()) {
                        try {
                            Files.copy(new File(backupDir.getPath() + ".zip").toPath(), new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName() + ".zip"));

                            long backupSize = Files.size(new File(backupDir.getPath() + ".zip").toPath());
                            incrementCurrentProgress(backupSize * copyProgressMultiplier);

                            try {
                                if (!new File(backupDir.getPath() + ".zip").delete()) {
                                    Logger.getLogger().warn("Can not delete backup in default directory", sender);
                                }
                                incrementCurrentProgress(backupSize * deleteProgressMultiplier);

                            } catch (Exception e) {
                                Logger.getLogger().warn("Can not delete backup in default directory", sender);
                            }
                        } catch (SecurityException e) {
                            Logger.getLogger().warn("Backup Directory is not allowed to modify!", sender);
                            Logger.getLogger().warn(this, e);
                        }
                    } else {

                        runCopyFilesInDir(new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile(), backupDir);

                        deleteDir(backupDir);
                    }
                }
                Logger.getLogger().devLog("Move task has been finished");
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

            // Delete old backups task
            {
                deleteOldBackups(backupsDir, false);
            }

            Logger.getLogger().success("Backup process has been finished successfully!", sender);
            UIUtils.successSound(sender);

            Backup.unlock();

            if (afterBackup.equals("RESTART")) {

                Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {
                    Scheduler.cancelTasks(Utils.plugin);
                    Bukkit.getServer().spigot().restart();
                }, 20);

            } else if (afterBackup.equals("STOP")) {

                Logger.getLogger().devLog("Stopping server...");
                Bukkit.shutdown();
            }

        } catch (Exception e) {

            BackupProcessStarter.setWorldsWritableSync(sender, false);

            Backup.unlock();

            Logger.getLogger().warn("The Backup process has been finished with an exception!", sender);
            Logger.getLogger().warn(this, e);
            UIUtils.cancelSound(sender);
        }
    }

    public void deleteOldBackups(File backupsDir, boolean onlyTask) {

        Backup.lock(this);

        try {
            if (Config.getInstance().getBackupsNumber() != 0 && backupsDir.listFiles() != null) {

                Logger.getLogger().devLog("Delete Old Backups 1 task has been started");

                ArrayList<LocalDateTime> backups = Backup.getBackups();
                Utils.sortLocalDateTime(backups);

                int backupsToDelete = backups.size() - Config.getInstance().getBackupsNumber();

                for (LocalDateTime fileName : backups) {

                    if (backupsToDelete <= 0) {

                        break;
                    }

                    for (File backup : Objects.requireNonNull(backupsDir.listFiles())) {

                        String backupFileName = backup.getName().replace(".zip", "");

                        while (backupFileName.length() < fileName.toString().length()) {

                            backupFileName = backupFileName.concat("0");
                        }

                        try {
                            if (LocalDateTime.parse(backupFileName, Backup.dateTimeFormatter).equals(fileName)) {

                                if (!backup.getName().endsWith(".zip")) {

                                    deleteDir(backup);

                                } else {

                                    if (!backup.delete()) {

                                        Logger.getLogger().warn("Failed to delete old backup !" + backup.getName(), sender);
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    backupsToDelete--;
                }
                Logger.getLogger().devLog("Delete Old Backups 1 task has been finished");
            }

            if (Config.getInstance().getBackupsWeight() != 0) {

                Logger.getLogger().devLog("Delete Old Backups 2 task has been started");

                long backupsFolderWeight = FileUtils.sizeOf(backupsDir);

                if (backupsFolderWeight > Config.getInstance().getBackupsWeight() && backupsDir.listFiles() != null) {

                    ArrayList<LocalDateTime> backups = Backup.getBackups();
                    Utils.sortLocalDateTime(backups);

                    long bytesToDelete = backupsFolderWeight - Config.getInstance().getBackupsWeight();

                    for (LocalDateTime fileName : backups) {

                        if (bytesToDelete <= 0) {

                            break;
                        }

                        if (backupsDir.listFiles() == null) {

                            Logger.getLogger().warn("Something went wrong while trying to delete old backup!", sender);
                        }

                        for (File backup : Objects.requireNonNull(backupsDir.listFiles())) {

                            String backupFileName = backup.getName().replace(".zip", "");

                            try {

                                if (LocalDateTime.parse(backupFileName, Backup.dateTimeFormatter).equals(fileName)) {

                                    bytesToDelete -= FileUtils.sizeOf(backup);

                                    if (!backup.getName().endsWith(".zip")) {

                                        deleteDir(backup);

                                    } else {

                                        if (!backup.delete()) {

                                            Logger.getLogger().warn("Failed to delete old backup !" + backup.getName(), sender);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                Logger.getLogger().devLog("Delete old backups 2 task has been finished");
            }

            if (onlyTask) {
                Backup.unlock();
                UIUtils.successSound(sender);
            }
        } catch (Exception e) {

            if (onlyTask) {
                Backup.unlock();
                UIUtils.cancelSound(sender);
            }
            Logger.getLogger().warn(BackupProcess.class, e);
        }
    }

    private void deleteDir(File dir) {

        if (!dir.exists()) {
            Logger.getLogger().warn("Directory " + dir.getAbsolutePath() + " does not exist");
            return;
        }

        if (dir.isFile()) {

            long fileByteSize = 0;

            try {
                fileByteSize = Files.size(dir.toPath());
            } catch (Exception e) {
                Logger.getLogger().warn("Failed to get file size before deletion", sender);
                Logger.getLogger().warn(BackupProcess.class, e);
            }

            if (!dir.delete()) {

                Logger.getLogger().warn("Can not delete file " + dir.getName(), sender);
            }

            incrementCurrentProgress(fileByteSize * deleteProgressMultiplier);
        }

        else if (dir.isDirectory()) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                if (file.isDirectory()) {

                    deleteDir(file);

                } else {

                    long fileByteSize = 0;

                    try {
                        fileByteSize = Files.size(file.toPath());
                    } catch (Exception e) {
                        Logger.getLogger().warn("Failed to get file size before deletion", sender);
                        Logger.getLogger().warn(BackupProcess.class, e);
                    }

                    if (!file.delete()) {

                        Logger.getLogger().warn("Can not delete file " + file.getName(), sender);
                    }

                    incrementCurrentProgress(fileByteSize * deleteProgressMultiplier);
                }
            }
            if (!dir.delete()) {

                Logger.getLogger().warn("Can not delete directory " + dir.getName(), sender);
            }
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path folderDir) {

        if (!sourceDir.exists()) {
            Logger.getLogger().warn("Directory " + sourceDir.getAbsolutePath() + " does not exist");
            return;
        }

        if (sourceDir.isFile()) {

            try {

                String relativeFilePath = folderDir.toAbsolutePath().relativize(sourceDir.toPath().toAbsolutePath()).toString();

                zip.putNextEntry(new ZipEntry(relativeFilePath));
                FileInputStream fileInputStream = new FileInputStream(sourceDir);
                byte[] buffer = new byte[4048];
                int length;

                while ((length = fileInputStream.read(buffer)) > 0) {
                    zip.write(buffer, 0, length);
                }
                zip.closeEntry();
                fileInputStream.close();

                incrementCurrentProgress(Files.size(sourceDir.toPath()) * zipProgressMultiplier);

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + sourceDir.getName(), sender);
                Logger.getLogger().warn(this, e);
            }
        }

        if (sourceDir.listFiles() == null) {
            return;
        }

        for (File file : sourceDir.listFiles()) {

            boolean isExcludedDirectory = Utils.isExcludedDirectory(file, sender);

            try {
                if (isExcludedDirectory || file.getCanonicalFile().equals(new File("plugins/Backuper/Backups").getCanonicalFile()) ||
                        file.getCanonicalFile().equals(new File(Config.getInstance().getBackupsFolder()).getCanonicalFile())) {
                    continue;
                }
            } catch (SecurityException e) {
                Logger.getLogger().warn("Failed to check \"excludeDirectoryFromBackup\" for file \"" + file.getAbsolutePath() + "\", no access", sender);
                Logger.getLogger().warn("BackupTask", e);
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying check \"excludeDirectoryFromBackup\" for file \"" + file.getAbsolutePath() + "\"", sender);
                Logger.getLogger().warn("BackupTask", e);
            }

            if (file.isDirectory()) {

                addDirToZip(zip, file, folderDir);

            } else if (!file.getName().equals("session.lock")) {

                try {

                    String relativeFilePath = folderDir.toAbsolutePath().relativize(file.toPath().toAbsolutePath()).toString();

                    zip.putNextEntry(new ZipEntry(relativeFilePath));
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[4048];
                    int length;

                    while ((length = fileInputStream.read(buffer)) > 0) {
                        zip.write(buffer, 0, length);
                    }
                    zip.closeEntry();
                    fileInputStream.close();

                    incrementCurrentProgress(Files.size(file.toPath()) * zipProgressMultiplier);

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + file.getName(), sender);
                    Logger.getLogger().warn(this, e);
                }
            }
        }
    }

    private synchronized void incrementCurrentProgress(long progress) {
        currentProgress += progress;
    }

    // For async copying
    private volatile ArrayList<Long> completedCopyTasks = new ArrayList<>();
    private long copyTasksCount = 0;

    private synchronized void runCopyFilesInDir(File destDir, File sourceDir) {

        copyTasksCount = 0;
        completedCopyTasks.clear();

        unsafeCopyFilesInDir(destDir, sourceDir);

        // Waiting for all files being copied
        while (completedCopyTasks.size() < copyTasksCount) {}
    }

    @Utility
    private void unsafeCopyFilesInDir(File destDir, File sourceDir) {

        if (!sourceDir.exists()) {
            Logger.getLogger().warn("Directory " + sourceDir.getAbsolutePath() + " does not exist", sender);
            return;
        }

        if (sourceDir.isFile()) {

            copyTasksCount++;

            final long taskNumber = copyTasksCount;

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

                try {

                    Files.copy(sourceDir.toPath(), destDir.toPath());

                    completedCopyTasks.add(taskNumber);

                    incrementCurrentProgress(Files.size(sourceDir.toPath()) * copyProgressMultiplier);

                } catch (SecurityException e) {

                    Logger.getLogger().warn("Backup Directory is not allowed to modify! " + sourceDir.getName(), sender);
                    Logger.getLogger().warn("BackupTask", e);

                    completedCopyTasks.add(taskNumber);

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong while trying to copy file! " + sourceDir.getName(), sender);
                    Logger.getLogger().warn("BackupTask", e);

                    completedCopyTasks.add(taskNumber);
                }
            });
        }

        if (sourceDir.listFiles() != null) {

            if (!destDir.mkdir()) {

                Logger.getLogger().warn("Can not create " + destDir.getPath() + " dir", sender);
            }

            for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

                boolean isExcludedDirectory = Utils.isExcludedDirectory(file, sender);

                try {
                    if (isExcludedDirectory || file.getCanonicalFile().equals(new File("plugins/Backuper/Backups").getCanonicalFile()) ||
                            file.getCanonicalFile().equals(new File(Config.getInstance().getBackupsFolder()).getCanonicalFile())) {
                        continue;
                    }
                } catch (SecurityException e) {
                    Logger.getLogger().warn("Failed to check \"excludeDirectoryFromBackup\" for file \"" + file.getAbsolutePath() + "\", no access", sender);
                    Logger.getLogger().warn("BackupTask", e);
                } catch (Exception e) {
                    Logger.getLogger().warn("Something went wrong while trying check \"excludeDirectoryFromBackup\" for file \"" + file.getAbsolutePath() + "\"", sender);
                    Logger.getLogger().warn("BackupTask", e);
                }

                if (file.isDirectory()) {

                    runCopyFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);

                } else if (!file.getName().equals("session.lock")) {

                    try {

                        Files.copy(file.toPath(), destDir.toPath().resolve(file.getName()));
                        incrementCurrentProgress(Files.size(file.toPath()) * copyProgressMultiplier);

                    } catch (SecurityException e) {

                        Logger.getLogger().warn("Backup Directory is not allowed to modify! " + file.getName(), sender);
                        Logger.getLogger().warn("BackupTask", e);

                    } catch (Exception e) {

                        Logger.getLogger().warn("Something went wrong while trying to copy file! " + file.getName(), sender);
                        Logger.getLogger().warn("BackupTask", e);
                    }
                }
            }
        }
    }

    private long calculateMaxProgress() {

        long maxProgress = 0;

        try {

            long fullByteSizeToBackup = 0;

            for (World world : Bukkit.getWorlds()) {

                long worldByteSize = Utils.getFolderOrFileByteSize(world.getWorldFolder());
                fullByteSizeToBackup += worldByteSize;

                if (Config.getInstance().isZipArchive()) {
                    maxProgress += worldByteSize * zipProgressMultiplier;
                }
                else {
                    maxProgress += worldByteSize * copyProgressMultiplier;
                }
            }

            for (String addDirectoryToBackup : Config.getInstance().getAddDirectoryToBackup()) {

                File addDirectoryToBackupFile = new File(addDirectoryToBackup);
                boolean isExcludedDirectory = Utils.isExcludedDirectory(addDirectoryToBackupFile, sender);

                if (!isExcludedDirectory) {

                    long addDirectoryToBackupByteSize = getFileFolderByteSizeExceptExcluded(addDirectoryToBackupFile);
                    fullByteSizeToBackup += addDirectoryToBackupByteSize;

                    if (Config.getInstance().isZipArchive()) {
                        maxProgress += addDirectoryToBackupByteSize * zipProgressMultiplier;
                    }
                    else {
                        maxProgress += addDirectoryToBackupByteSize * copyProgressMultiplier;
                    }
                }
            }

            if (!new File(Config.getInstance().getBackupsFolder()).getCanonicalFile()
                    .equals(new File("plugins/Backuper/Backups").getCanonicalFile())) {

                maxProgress += fullByteSizeToBackup * deleteProgressMultiplier;
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to calculate target progress in BackupProcess");
            Logger.getLogger().warn(BackupProcess.class, e);
        }

        return maxProgress;
    }

    private long getFileFolderByteSizeExceptExcluded(File path) {

        if (!path.exists()) {
            Logger.getLogger().warn("Directory " + path.getAbsolutePath() + " does not exist");
            return 0;
        }

        boolean isExcludedDirectory = Utils.isExcludedDirectory(path, sender);

        if (isExcludedDirectory) {
            return 0;
        }

        if (!path.isDirectory()) {
            try {
                return Files.size(path.toPath());
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying to calculate backup size!");
                Logger.getLogger().warn(BackupProcess.class, e);
                return 0;
            }
        }

        long size = 0;

        if (path.isDirectory()) {
            for (File file : Objects.requireNonNull(path.listFiles())) {
                size += getFileFolderByteSizeExceptExcluded(file);
            }
        }

        return size;
    }
}