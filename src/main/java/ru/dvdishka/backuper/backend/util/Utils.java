package ru.dvdishka.backuper.backend.util;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.LocalStorage;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class Utils {

    public static final Properties properties = new Properties();

    public static boolean errorSetWritable = false;
    public static volatile HashMap<String, Boolean> isAutoSaveEnabled = new HashMap<>();

    static {
        try {
            properties.load(Utils.class.getClassLoader().getResourceAsStream("project.properties"));
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().devWarn("Failed to load properties!");
            Backuper.getInstance().getLogManager().warn(e);
        }
    }

    public static final List<String> downloadLinks = List.of("https://modrinth.com/plugin/backuper/versions#all-versions",
            "https://hangar.papermc.io/Collagen/Backuper");
    public static final List<String> downloadLinksName = List.of("Modrinth", "Hangar");
    public static URL getLatestVersionURL = null;

    static {
        try {
            getLatestVersionURL = URI.create("https://hangar.papermc.io/api/v1/projects/Collagen/Backuper/latestrelease").toURL();
        } catch (MalformedURLException e) {
            Backuper.getInstance().getLogManager().warn("Failed to check Backuper updates!");
            Backuper.getInstance().getLogManager().warn(e);
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
                Backuper.getInstance().getLogManager().warn("Something went wrong while trying to calculate file size!");
                Backuper.getInstance().getLogManager().warn(e);
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
        if (!path.exists() || Utils.isExcludedDirectory(path, null)) return 0;

        if (!path.isDirectory()) {
            try {
                return Files.size(path.toPath());
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("Something went wrong while trying to calculate backup size!");
                Backuper.getInstance().getLogManager().warn(e);
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
        if (!path.exists()) return true;

        boolean isExcludedDirectory = false;
        try {
            Path normalizedPath =  path.toPath().toAbsolutePath().normalize();
            List<LocalStorage> localStorages = Backuper.getInstance().getStorageManager().getStorages().stream()
                    .filter(storage -> storage instanceof LocalStorage).map(storage -> (LocalStorage) storage).toList();
            List<Path> normalizedBackupFolderPaths = localStorages.stream()
                    .map(storage -> new File(storage.getConfig().getBackupsFolder()).toPath().toAbsolutePath().normalize())
                    .toList();

            if (localStorages.stream().anyMatch(storage -> path.toPath().startsWith(new File(storage.getConfig().getBackupsFolder()).toPath())) ||
                    normalizedBackupFolderPaths.stream().anyMatch(normalizedPath::startsWith) ||
                    localStorages.stream().anyMatch(storage -> !Utils.isWindows && path.toPath().startsWith(new File("./%s".formatted(storage.getConfig().getBackupsFolder())).toPath())) ||
                    localStorages.stream().anyMatch(storage -> Utils.isWindows && path.toPath().startsWith(new File(storage.getConfig().getBackupsFolder()).toPath())) ||
                    localStorages.stream().anyMatch(storage -> Utils.isWindows && storage.getConfig().getBackupsFolder().charAt(1) != ':' &&
                            path.toPath().startsWith(new File(".\\%s".formatted(storage.getConfig().getBackupsFolder())).toPath()))) {
                return true;
            }
        } catch (SecurityException e) {
            Backuper.getInstance().getLogManager().warn("Failed to copy file \"%s\", no access".formatted(path.getAbsolutePath()), sender);
            Backuper.getInstance().getLogManager().warn(e);
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Something went wrong while trying to copy file \"%s\"".formatted(path.getAbsolutePath()), sender);
            Backuper.getInstance().getLogManager().warn(e);
        }

        for (String excludeDirectoryFromBackup : Backuper.getInstance().getConfigManager().getBackupConfig().getExcludeDirectoryFromBackup()) {
            try {
                File excludeDirectoryFromBackupFile = Paths.get(excludeDirectoryFromBackup).toFile().getCanonicalFile();
                if (path.getCanonicalFile().toPath().startsWith(excludeDirectoryFromBackupFile.toPath())) {
                    isExcludedDirectory = true;
                }
            } catch (SecurityException e) {
                Backuper.getInstance().getLogManager().warn("Failed to copy file \"%s\", no access".formatted(path.getAbsolutePath()), sender);
                Backuper.getInstance().getLogManager().warn(e);
                return true;
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("Something went wrong while trying to copy file \"%s\"".formatted(path.getAbsolutePath()), sender);
                Backuper.getInstance().getLogManager().warn(e);
                return true;
            }
        }

        return isExcludedDirectory;
    }
}
