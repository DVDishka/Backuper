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
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.backend.config.Config;

class BackupProcess implements Runnable, Task {

    private final String afterBackup;
    private final CommandSender sender;
    private final boolean isAutoBackup;
    private volatile long maxProgress = 0;
    private volatile long currentProgress = 0;

    private final String taskName;

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

                                copyFilesInDir(backupDir.toPath().resolve(world.getName()).toFile(), worldDir);
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

                            copyFilesInDir(backupDir.toPath().resolve(additionalDirectoryToBackupFile.getName()).toFile(), additionalDirectoryToBackupFile);
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
                            incrementBackuppedByteSize(maxProgress / 2);
                            try {
                                if (!new File(backupDir.getPath() + ".zip").delete()) {
                                    Logger.getLogger().warn("Can not delete backup in default directory", sender);
                                }
                            } catch (Exception e) {
                                Logger.getLogger().warn("Can not delete backup in default directory", sender);
                            }
                        } catch (SecurityException e) {
                            Logger.getLogger().warn("Backup Directory is not allowed to modify!", sender);
                            Logger.getLogger().warn(this, e);
                        }
                    } else {
                        copyFilesInDir(new File(Config.getInstance().getBackupsFolder()).toPath().resolve(backupDir.getName()).toFile(), backupDir);
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
        }
    }

    public void deleteOldBackups(File backupsDir, boolean onlyTask) {

        Backup.lock(this);

        try {
            if (Config.getInstance().getBackupsNumber() != 0 && backupsDir.listFiles() != null) {

                Logger.getLogger().devLog("Delete Old Backups 1 task has been started");

                ArrayList<LocalDateTime> backups = Utils.getBackups();
                Backup.sortLocalDateTime(backups);

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

                    ArrayList<LocalDateTime> backups = Utils.getBackups();
                    Backup.sortLocalDateTime(backups);

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
            }

            Logger.getLogger().devLog("Delete old backups 2 task has been finished");
            if (onlyTask) {
                Backup.unlock();
            }
        } catch (Exception e) {

            if (onlyTask) {
                Backup.unlock();
            }
            Logger.getLogger().warn(BackupProcess.class, e);
        }
    }

    private void deleteDir(File dir) {

        if (dir != null && dir.listFiles() != null) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                if (file.isDirectory()) {

                    deleteDir(file);

                } else {

                    if (!file.delete()) {

                        Logger.getLogger().warn("Can not delete file " + file.getName(), sender);
                    }
                }
            }
            if (!dir.delete()) {

                Logger.getLogger().warn("Can not delete directory " + dir.getName(), sender);
            }
        }
    }

    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path folderDir) {

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

                incrementBackuppedByteSize(Files.size(sourceDir.toPath()));

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

                    incrementBackuppedByteSize(Files.size(file.toPath()));

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + file.getName(), sender);
                    Logger.getLogger().warn(this, e);
                }
            }
        }
    }

    private synchronized void incrementBackuppedByteSize(long size) {
        currentProgress += size;
    }

    private void copyFilesInDir(File destDir, File sourceDir) {

        if (sourceDir.isFile()) {

            try {

                Files.copy(sourceDir.toPath(), destDir.toPath());
                incrementBackuppedByteSize(Files.size(sourceDir.toPath()));

            } catch (SecurityException e) {

                Logger.getLogger().warn("Backup Directory is not allowed to modify! " + sourceDir.getName(), sender);
                Logger.getLogger().warn("BackupTask", e);

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while trying to copy file! " + sourceDir.getName(), sender);
                Logger.getLogger().warn("BackupTask", e);
            }
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

                    copyFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);

                } else if (!file.getName().equals("session.lock")) {

                    try {

                        Files.copy(file.toPath(), destDir.toPath().resolve(file.getName()));
                        incrementBackuppedByteSize(Files.size(file.toPath()));

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

            for (World world : Bukkit.getWorlds()) {
                maxProgress += Utils.getFolderOrFileByteSize(world.getWorldFolder());
            }

            for (String addDirectoryToBackup : Config.getInstance().getAddDirectoryToBackup()) {

                File addDirectoryToBackupFile = new File(addDirectoryToBackup);
                boolean isExcludedDirectory = Utils.isExcludedDirectory(addDirectoryToBackupFile, sender);

                if (!isExcludedDirectory) {
                    maxProgress += getFileFolderByteSizeExceptExcluded(addDirectoryToBackupFile);
                }
            }

            if (!new File(Config.getInstance().getBackupsFolder()).getCanonicalFile()
                    .equals(new File("plugins/Backuper/Backups").getCanonicalFile())) {

                maxProgress *= 2;
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to calculate target progress in BackupProcess");
            Logger.getLogger().warn(BackupProcess.class, e);
        }

        return maxProgress;
    }

    private long getFileFolderByteSizeExceptExcluded(File path) {

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