package ru.dvdishka.backuper.common;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Properties;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Common {

    public static Plugin plugin;
    public static Properties properties = new Properties();
    public static boolean isBackupRunning = false;
    static {
        try {
            properties.load(Common.class.getClassLoader().getResourceAsStream("project.properties"));
        } catch (Exception e) {
            Logger.getLogger().devWarn("Common", "Failed to load properties!");
            Logger.getLogger().devWarn("Common", e);        }
    }

    public static final int bStatsId = 17735;
    public static final String downloadLink = "https://hangar.papermc.io/Collagen/Backuper";
    public static URL getLatestVersionURL = null;
    static {
        try {
            getLatestVersionURL = new URL("https://hangar.papermc.io/api/v1/projects/Collagen/Backuper/latestrelease");
        } catch (MalformedURLException e) {
            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().devWarn("Common", e);
        }
    }

    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public static boolean isFolia = false;

    public static String getProperty(String property) {
        return properties.getProperty(property);
    }

    public static void sortLocalDateTime(ArrayList<LocalDateTime> backups) {
        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).isAfter(backups.get(secondBackupsIndex))) {

                    LocalDateTime saveDate = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, saveDate);
                }
            }
        }
    }

    public static void sortLocalDateTimeDecrease(ArrayList<LocalDateTime> backups) {
        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).isBefore(backups.get(secondBackupsIndex))) {

                    LocalDateTime saveDate = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, saveDate);
                }
            }
        }
    }

    public static long getPathOrFileByteSize(File path) {

        if (!path.isDirectory()) {
            try {
                return Files.size(path.toPath());
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying to calculate backup size!");
                Logger.getLogger().devWarn("Common", e);
            }
        }

        long size = 0;

        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                size += getPathOrFileByteSize(file);
            }
        }

        return size;
    }

    public static boolean checkBackupExistanceByName(String backupName) {

        try {
            LocalDateTime.parse(backupName, Common.dateTimeFormatter);
        } catch (Exception e) {
            return false;
        }

        File backupsFolder = new File(ConfigVariables.backupsFolder);
        String backupFilePath;

        return backupsFolder.toPath().resolve(backupName).toFile().exists() ||
                backupsFolder.toPath().resolve(backupName + ".zip").toFile().exists();
    }

    public static long getBackupMBSizeByName(String backupName) {

        File backupsFolder = new File(ConfigVariables.backupsFolder);
        String backupFilePath;

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath();
        } else {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath() + ".zip";
        }

        long backupSize = Common.getPathOrFileByteSize(new File(backupFilePath));

        if (backupSize != 0) {
            backupSize /= (1024 * 1024);
        }

        return backupSize;
    }

    public static String zipOrFolderBackupByName(String backupName) {

        File backupsFolder = new File(ConfigVariables.backupsFolder);
        String zipOrFolder = "(ZIP)";

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            zipOrFolder = "(Folder)";
        }

        return zipOrFolder;
    }

    public static File getBackupFileByName(String backupName) {

        if (!Common.checkBackupExistanceByName(backupName)) {
            return null;
        }

        File backupsFolder = new File(ConfigVariables.backupsFolder);

        if (Common.zipOrFolderBackupByName(backupName).equals("(ZIP)")) {
            return backupsFolder.toPath().resolve(backupName + ".zip").toFile();
        } else {
            return backupsFolder.toPath().resolve(backupName).toFile();
        }
    }

    public static void returnFailure(String message, CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.RED + message);
        } catch (Exception ignored) {}
    }

    public static void returnSuccess(String message, CommandSender sender) {
        try {
            sender.sendMessage(ChatColor.GREEN + message);
        } catch (Exception ignored) {}
    }

    public static void returnSuccess(String message, @NotNull CommandSender sender, ChatColor color) {
        try {
            sender.sendMessage(color + message);
        } catch (Exception ignored) {}
    }

    public static void returnFailure(String message, @NotNull CommandSender sender, ChatColor color) {
        try {
            sender.sendMessage(color + message);
        } catch (Exception ignored) {}
    }
}
