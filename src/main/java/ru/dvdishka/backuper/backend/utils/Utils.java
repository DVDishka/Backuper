package ru.dvdishka.backuper.backend.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

public class Utils {

    public static Plugin plugin;
    public static final Properties properties = new Properties();

    public static boolean errorSetWritable = false;
    public static volatile HashMap<String, Boolean> isAutoSaveEnabled = new HashMap<>();

    static {
        try {
            properties.load(Utils.class.getClassLoader().getResourceAsStream("project.properties"));
        } catch (Exception e) {
            Logger.getLogger().devWarn(Utils.class, "Failed to load properties!");
            Logger.getLogger().warn(Utils.class, e);
        }
    }

    public static final int bStatsId = 17735;
    public static final List<String> downloadLinks = List.of("https://modrinth.com/plugin/backuper/versions#all-versions",
            "https://hangar.papermc.io/Collagen/Backuper");
    public static final List<String> downloadLinksName = List.of("Modrinth", "Hangar");
    public static URL getLatestVersionURL = null;
    public static boolean isUpdatedToLatest = true;
    static {
        try {
            getLatestVersionURL = new URL("https://hangar.papermc.io/api/v1/projects/Collagen/Backuper/latestrelease");
        } catch (MalformedURLException e) {
            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().warn(Utils.class, e);
        }
    }

    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public static boolean isFolia = false;

    public static String getProperty(String property) {
        return properties.getProperty(property);
    }

    public static long getFileFolderByteSize(File path) {

        if (!path.exists()) {
            return 0;
        }

        if (!path.isDirectory()) {
            try {
                return Files.size(path.toPath());
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying to calculate file size!");
                Logger.getLogger().warn(Utils.class, e);
                return 0;
            }
        }

        long size = 0;

        if (path.isDirectory()) {
            for (File file : Objects.requireNonNull(path.listFiles())) {
                size += getFileFolderByteSize(file);
            }
        }

        return size;
    }

    public static long getFileFolderByteSizeExceptExcluded(File path) {

        if (!path.exists()) {
            Logger.getLogger().warn("Directory " + path.getAbsolutePath() + " does not exist");
            return 0;
        }

        boolean isExcludedDirectory = Utils.isExcludedDirectory(path, null);

        if (isExcludedDirectory) {
            return 0;
        }

        if (!path.isDirectory()) {
            try {
                return Files.size(path.toPath());
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying to calculate backup size!");
                Logger.getLogger().warn(Utils.class, e);
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

    public static boolean isExcludedDirectory(File path, CommandSender sender) {

        if (!path.exists()) {
            return true;
        }

        boolean isExcludedDirectory = false;

        try {

            if (path.toPath().startsWith(new File(Config.getInstance().getLocalConfig().getBackupsFolder()).toPath()) ||
                    path.toPath().startsWith(new File("plugins/Backuper/Backups/").toPath())) {
                return true;
            }

        } catch (SecurityException e) {
            Logger.getLogger().warn("Failed to copy file \"" + path.getAbsolutePath() + "\", no access", sender);
            Logger.getLogger().warn("BackupTask", e);
        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong while trying to copy file \"" + path.getAbsolutePath() + "\"", sender);
            Logger.getLogger().warn("BackupTask", e);
        }

        for (String excludeDirectoryFromBackup : Config.getInstance().getExcludeDirectoryFromBackup()) {

            try {

                File excludeDirectoryFromBackupFile = Paths.get(excludeDirectoryFromBackup).toFile().getCanonicalFile();

                if (path.getCanonicalFile().toPath().startsWith(excludeDirectoryFromBackupFile.toPath())) {
                    isExcludedDirectory = true;
                }

            } catch (SecurityException e) {
                Logger.getLogger().warn("Failed to copy file \"" + path.getAbsolutePath() + "\", no access", sender);
                Logger.getLogger().warn("Utils", e);
                return true;
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying to copy file \"" + path.getAbsolutePath() + "\"", sender);
                Logger.getLogger().warn("Utils", e);
                return true;
            }
        }

        return isExcludedDirectory;
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
}
