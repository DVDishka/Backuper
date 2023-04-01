package ru.dvdishka.backuper.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import ru.dvdishka.backuper.common.CommonVariables;
import ru.dvdishka.backuper.common.ConfigVariables;

public class BackuperAsyncTask implements Runnable {

    private String afterBackup = "NOTHING";

    public BackuperAsyncTask(String afterBackup) {

        this.afterBackup = afterBackup;
    }

    public void run() {

        try {

            File backupDir = new File("plugins/Backuper/Backups/" +
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            File backupsDir = new File("plugins/Backuper/Backups");

            if (!ConfigVariables.zipArchive && !backupDir.mkdir()) {

                CommonVariables.logger.warning("Can not create " + backupDir.getPath() + " dir!");
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

                        CommonVariables.logger.warning("Something went wrong when trying to copy files!");
                        CommonVariables.logger.warning(e.getMessage());
                    }
                }
            }

            if (ConfigVariables.zipArchive) {

                zipOutputStream.close();
            }

            for (World world : Bukkit.getWorlds()) {

                if (!world.getWorldFolder().setWritable(true)) {

                    CommonVariables.logger.warning("Can not set " + world.getWorldFolder().getPath() + " writable!");
                }
            }

            CommonVariables.logger.info("Backup process has been finished!");

            if (ConfigVariables.backupsNumber != 0 && backupsDir.listFiles() != null) {

                ArrayList<LocalDateTime> backups = new ArrayList<>();

                for (File file : backupsDir.listFiles()) {

                    try {

                        String fileName = file.getName().replace(".zip", "");

                        backups.add(LocalDateTime.parse(fileName));

                    } catch (Exception ignored) {}
                }

                for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

                    for (int secondBackupsIndex = 0; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                        if (backups.get(firstBackupsIndex).isBefore(backups.get(secondBackupsIndex))) {

                            LocalDateTime saveDate = backups.get(firstBackupsIndex);

                            backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                            backups.set(secondBackupsIndex, saveDate);
                        }
                    }
                }

                int backupsToDelete = backups.size() - ConfigVariables.backupsNumber;

                for (LocalDateTime fileName : backups) {

                    if (backupsToDelete <= 0) {

                        break;
                    }

                    try {

                        for (File backup : backupsDir.listFiles()) {

                            String backupFileName = backup.getName().replace(".zip", "");

                            while (backupFileName.length() < fileName.toString().length()) {

                                backupFileName = backupFileName.concat("0");
                            }

                            if (backupFileName.equals(fileName.toString())) {

                                if (!backup.getName().endsWith(".zip")) {

                                    deleteDir(backup);

                                } else {

                                    if (!backup.delete()) {

                                        CommonVariables.logger.warning("Failed to delete old backup !" + backup.getName());
                                    }
                                }                            }
                        }
                    } catch (Exception e) {

                        CommonVariables.logger.warning(e.getMessage());
                    }

                    backupsToDelete--;
                }
            }

            if (ConfigVariables.backupsWeight != 0) {

                long backupsFolderWeight = FileUtils.sizeOf(backupsDir);

                if (backupsFolderWeight > ConfigVariables.backupsWeight && backupsDir.listFiles() != null) {

                    ArrayList<LocalDateTime> backups = new ArrayList<>();

                    for (File file : backupsDir.listFiles()) {

                        try {

                            String fileName = file.getName().replace(".zip", "");

                            backups.add(LocalDateTime.parse(fileName));

                        } catch (Exception ignored) {}
                    }

                    for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

                        for (int secondBackupsIndex = 0; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                            if (backups.get(firstBackupsIndex).isBefore(backups.get(secondBackupsIndex))) {

                                LocalDateTime saveDate = backups.get(firstBackupsIndex);

                                backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                                backups.set(secondBackupsIndex, saveDate);
                            }
                        }
                    }

                    long bytesToDelete = backupsFolderWeight - ConfigVariables.backupsWeight;

                    for (LocalDateTime fileName : backups) {

                        if (bytesToDelete <= 0) {

                            break;
                        }

                        if (backupsDir.listFiles() == null) {

                            CommonVariables.logger.warning("Something went wrong while trying to delete old backup!");
                        }

                        for (File backup : backupsDir.listFiles()) {

                            String backupFileName = backup.getName().replace(".zip",  "");

                            while (backupFileName.length() < fileName.toString().length()) {

                                backupFileName = backupFileName.concat("0");
                            }

                            if (backupFileName.equals(fileName.toString())) {

                                bytesToDelete -= FileUtils.sizeOf(backup);

                                if (!backup.getName().endsWith(".zip")) {

                                    deleteDir(backup);

                                } else {

                                    if (!backup.delete()) {

                                        CommonVariables.logger.warning("Failed to delete old backup !" + backup.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (afterBackup.equals("RESTART")) {

                Bukkit.getGlobalRegionScheduler().runDelayed(CommonVariables.plugin, (task) -> {
                    new RestartSafelyTask().run();
                }, 20);

            } else if (afterBackup.equals("STOP")) {

                Bukkit.getServer().shutdown();
            }

        } catch (Exception e) {

            for (World world : Bukkit.getWorlds()) {

                if (!world.getWorldFolder().setWritable(true)) {

                    CommonVariables.logger.warning("Can not set " + world.getWorldFolder().getPath() + " writable!");
                }
            }

            CommonVariables.logger.warning("Copy task has finished with an exception!");
            CommonVariables.logger.warning(e.getMessage());
        }

    }

    public void deleteDir(File dir) {

        if (dir != null && dir.listFiles() != null) {

            for (File file : dir.listFiles()) {

                if (file.isDirectory()) {

                    deleteDir(file);

                } else {

                    if (!file.delete()) {

                        CommonVariables.logger.warning("Can not delete file " + file.getName());
                    }
                }
            }
            if (!dir.delete()) {

                CommonVariables.logger.warning("Can not delete directory " + dir.getName());
            }
        }
    }

    public void addDirToZip(ZipOutputStream zip, File sourceDir, Path zipFilePath) {

        for (File file : sourceDir.listFiles()) {

            if (file.isDirectory()) {

                addDirToZip(zip, file, zipFilePath);

            } else {

                try {

                    String relativeFilePath = zipFilePath.relativize(file.toPath()).toFile().getPath();
                    relativeFilePath = relativeFilePath.replace("./", "");
                    relativeFilePath = relativeFilePath.replaceFirst(".", "");
                    while (relativeFilePath.length() > 0 && relativeFilePath.charAt(0) == '.') {

                        relativeFilePath = relativeFilePath.replaceFirst(".", "");
                    }

                    zip.putNextEntry(new ZipEntry(relativeFilePath));
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[4048];
                    int length = 0;

                    while ((length = fileInputStream.read(buffer)) > 0) {

                        zip.write(buffer, 0, length);
                    }
                    zip.closeEntry();
                    fileInputStream.close();

                } catch (Exception e) {

                    CommonVariables.logger.warning("Something went wrong while trying to put file in ZIP!");
                }
            }
        }
    }

    public void copyFilesInDir(File destDir, File sourceDir) {

        if (sourceDir.listFiles() != null) {

            if (!destDir.mkdir()) {

                CommonVariables.logger.warning("Can not create " + destDir.getPath() + " dir");
            }

            for (File file : sourceDir.listFiles()) {

                if (file.isDirectory()) {

                    copyFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);

                } else {

                    try {

                        Files.copy(file.toPath(), destDir.toPath().resolve(file.getName()));

                    } catch (Exception e) {

                        CommonVariables.logger.warning("Something went wrong while trying to copy file!");
                        CommonVariables.logger.warning(e.getMessage());
                    }
                }
            }
        }
    }
}
