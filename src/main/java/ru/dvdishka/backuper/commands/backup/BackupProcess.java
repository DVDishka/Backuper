package ru.dvdishka.backuper.commands.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.ConfigVariables;
import ru.dvdishka.backuper.common.Logger;
import ru.dvdishka.backuper.commands.common.Scheduler;

public class BackupProcess implements Runnable {

    private final String afterBackup;
    private final CommandSender sender;
    private final boolean isAutoBackup;

    public BackupProcess(String afterBackup, boolean isAutoBackup, CommandSender sender) {
        this.afterBackup = afterBackup;
        this.isAutoBackup = isAutoBackup;
        this.sender = sender;
    }

    public void returnFailure(String message) {
        Common.returnFailure(message, sender);
    }

    public void returnSuccess(String message) {
        Common.returnSuccess(message, sender);
    }

    public void sendMessage(String message) {Common.sendMessage(message, sender);}

    public void run() {

        try {

            File backupDir = new File("plugins/Backuper/Backups/" +
                    LocalDateTime.now().format(Backup.dateTimeFormatter));
            File backupsDir = new File(ConfigVariables.backupsFolder);

            {
                Logger.getLogger().devLog("Copy/Zip task has been started");

                if (!ConfigVariables.zipArchive && !backupDir.mkdir()) {

                    Logger.getLogger().warn("Can not create " + backupDir.getPath() + " dir!", sender);
                }

                FileOutputStream fileOutputStream;
                ZipOutputStream zipOutputStream = null;

                if (ConfigVariables.zipArchive) {

                    fileOutputStream = new FileOutputStream(backupDir.getPath() + ".zip");
                    zipOutputStream = new ZipOutputStream(fileOutputStream);
                }

                for (World world : Bukkit.getWorlds()) {

                    File worldDir = world.getWorldFolder();

                    if (worldDir.listFiles() != null) {

                        try {

                            if (ConfigVariables.zipArchive) {

                                addDirToZip(zipOutputStream, worldDir, new File(backupDir.getPath() + ".zip").toPath());

                            } else {

                                copyFilesInDir(backupDir.toPath().resolve(world.getName()).toFile(), worldDir);
                            }

                        } catch (Exception e) {

                            Logger.getLogger().warn("Something went wrong when trying to copy files!", sender);
                            Logger.getLogger().devWarn(this, e);
                        }
                    }
                }

                if (ConfigVariables.zipArchive) {

                    assert zipOutputStream != null;
                    zipOutputStream.close();
                }
                Logger.getLogger().devLog("Copy/Zip task has been finished");
            }

            {
                Logger.getLogger().devLog("Set writable task has been started");
                for (World world : Bukkit.getWorlds()) {

                    if (!world.getWorldFolder().setWritable(true)) {
                        Logger.getLogger().warn("Can not set folder writable!", sender);
                    }
                    world.setAutoSave(BackupProcessStarter.isAutoSaveEnabled.get(world.getName()));
                }
                Logger.getLogger().devLog("Set writable task has been finished");
            }

            {
                Logger.getLogger().devLog("Move task has been started");
                if (!ConfigVariables.backupsFolder.equals("plugins/Backuper/Backups")) {

                    if (ConfigVariables.zipArchive) {
                        try {
                            Files.copy(new File(backupDir.getPath() + ".zip").toPath(), new File(ConfigVariables.backupsFolder).toPath().resolve(backupDir.getName() + ".zip"));
                            try {
                                if (!new File(backupDir.getPath() + ".zip").delete()) {
                                    Logger.getLogger().warn("Can not delete backup in default directory", sender);
                                }
                            } catch (Exception e) {
                                Logger.getLogger().warn("Can not delete backup in default directory", sender);
                            }
                        } catch (SecurityException e) {
                            Logger.getLogger().warn("Backup Directory is not allowed to modify!", sender);
                            Logger.getLogger().devWarn(this, e);
                        }
                    } else {
                        copyFilesInDir(new File(ConfigVariables.backupsFolder).toPath().resolve(backupDir.getName()).toFile(), backupDir);
                        deleteDir(backupDir);
                    }
                }
                Logger.getLogger().devLog("Move task has been finished");
            }

            {
                if (isAutoBackup) {
                    Logger.getLogger().devLog("Update \"lastBackup\" Variable task has been started");
                    File configFile = new File("plugins/Backuper/config.yml");
                    FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                    config.set("lastBackup", LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
                    config.save(configFile);
                    Logger.getLogger().devLog("Update \"lastBackup\" Variable task has been finished");
                }
            }

            {
                if (ConfigVariables.backupsNumber != 0 && backupsDir.listFiles() != null) {

                    Logger.getLogger().devLog("Delete Old Backups 1 task has been started");

                    ArrayList<LocalDateTime> backups = Common.getBackups();

                    int backupsToDelete = backups.size() - ConfigVariables.backupsNumber;

                    for (LocalDateTime fileName : backups) {

                        if (backupsToDelete <= 0) {

                            break;
                        }

                        try {

                            for (File backup : Objects.requireNonNull(backupsDir.listFiles())) {

                                String backupFileName = backup.getName().replace(".zip", "");

                                while (backupFileName.length() < fileName.toString().length()) {

                                    backupFileName = backupFileName.concat("0");
                                }

                                if (LocalDateTime.parse(backupFileName, ru.dvdishka.backuper.common.Backup.dateTimeFormatter).equals(fileName)) {

                                    if (!backup.getName().endsWith(".zip")) {

                                        deleteDir(backup);

                                    } else {

                                        if (!backup.delete()) {

                                            Logger.getLogger().warn("Failed to delete old backup !" + backup.getName(), sender);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {

                            Logger.getLogger().devWarn(this, e);
                        }

                        backupsToDelete--;
                    }
                    Logger.getLogger().devLog("Delete Old Backups 1 task has been finished");
                }
            }

            {
                if (ConfigVariables.backupsWeight != 0) {

                    Logger.getLogger().devLog("Delete Old Backups 2 task has been started");

                    long backupsFolderWeight = FileUtils.sizeOf(backupsDir);

                    if (backupsFolderWeight > ConfigVariables.backupsWeight && backupsDir.listFiles() != null) {

                        ArrayList<LocalDateTime> backups = Common.getBackups();
                        ru.dvdishka.backuper.common.Backup.sortLocalDateTime(backups);

                        long bytesToDelete = backupsFolderWeight - ConfigVariables.backupsWeight;

                        for (LocalDateTime fileName : backups) {

                            if (bytesToDelete <= 0) {

                                break;
                            }

                            if (backupsDir.listFiles() == null) {

                                Logger.getLogger().warn("Something went wrong while trying to delete old backup!", sender);
                            }

                            for (File backup : Objects.requireNonNull(backupsDir.listFiles())) {

                                String backupFileName = backup.getName().replace(".zip", "");

                                while (backupFileName.length() < fileName.toString().length()) {

                                    backupFileName = backupFileName.concat("0");
                                }

                                if (LocalDateTime.parse(backupFileName, ru.dvdishka.backuper.common.Backup.dateTimeFormatter).equals(fileName)) {

                                    bytesToDelete -= FileUtils.sizeOf(backup);

                                    if (!backup.getName().endsWith(".zip")) {

                                        deleteDir(backup);

                                    } else {

                                        if (!backup.delete()) {

                                            Logger.getLogger().warn("Failed to delete old backup !" + backup.getName(), sender);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Logger.getLogger().devLog("Delete old backups 2 task has been finished");
                }
            }

            Logger.getLogger().success("Backup process has been finished successfully!", sender);

            Backup.isBackupBusy = false;

            if (afterBackup.equals("RESTART")) {

                Scheduler.getScheduler().runSyncDelayed(Common.plugin, () -> {
                    Scheduler.cancelTasks(Common.plugin);
                    Bukkit.getServer().spigot().restart();
                }, 20);

            } else if (afterBackup.equals("STOP")) {

                Logger.getLogger().devLog("Stopping server...");
                Bukkit.shutdown();
            }

        } catch (Exception e) {

            for (World world : Bukkit.getWorlds()) {
                if (!world.getWorldFolder().setWritable(true)) {
                    Logger.getLogger().warn("Can not set folder writable!", sender);
                }
                world.setAutoSave(BackupProcessStarter.isAutoSaveEnabled.get(world.getName()));
            }

            Backup.isBackupBusy = false;

            returnFailure("The Backup process has been finished with an exception, check the server console to get more info");
            Logger.getLogger().warn("The Backup process has been finished with an exception!");
            Logger.getLogger().devWarn(this, e);
        }
    }

    public void deleteDir(File dir) {

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

    public void addDirToZip(ZipOutputStream zip, File sourceDir, Path zipFilePath) {

        for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

            if (file.isDirectory()) {

                addDirToZip(zip, file, zipFilePath);

            } else if (!file.getName().equals("session.lock")) {

                try {

                    String relativeFilePath = zipFilePath.relativize(file.toPath()).toFile().getPath();
                    relativeFilePath = relativeFilePath.replace("./", "");
                    relativeFilePath = relativeFilePath.replace("..\\", "");
                    while (!relativeFilePath.isEmpty() && relativeFilePath.charAt(0) == '.') {

                        relativeFilePath = relativeFilePath.replaceFirst(".", "");
                    }

                    zip.putNextEntry(new ZipEntry(relativeFilePath));
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[4048];
                    int length;

                    while ((length = fileInputStream.read(buffer)) > 0) {

                        zip.write(buffer, 0, length);
                    }
                    zip.closeEntry();
                    fileInputStream.close();

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + file.getName(), sender);
                    Logger.getLogger().devWarn(this, e);
                }
            }
        }
    }

    public void copyFilesInDir(File destDir, File sourceDir) {

        if (sourceDir.listFiles() != null) {

            if (!destDir.mkdir()) {

                Logger.getLogger().warn("Can not create " + destDir.getPath() + " dir", sender);
            }

            for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

                if (file.isDirectory()) {

                    copyFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);

                } else if (!file.getName().equals("session.lock")) {

                    try {

                        Files.copy(file.toPath(), destDir.toPath().resolve(file.getName()));

                    } catch (SecurityException e) {

                        Logger.getLogger().warn("Backup Directory is not allowed to modify! " + file.getName(), sender);
                        Logger.getLogger().devWarn("BackupTask", e);

                    } catch (Exception e) {

                        Logger.getLogger().warn("Something went wrong while trying to copy file! " + file.getName(), sender);
                        Logger.getLogger().devWarn("BackupTask", e);
                    }
                }
            }
        }
    }
}