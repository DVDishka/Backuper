package ru.dvdishka.backuper.backend.utils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.dvdishka.backuper.backend.config.Config;

public class Utils {

    public static Plugin plugin;
    public static final Properties properties = new Properties();

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

    public static long getFolderOrFileByteSize(File path) {

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
                size += getFolderOrFileByteSize(file);
            }
        }

        return size;
    }

    public static boolean isExcludedDirectory(File path, CommandSender sender) {

        boolean isExcludedDirectory = false;

        for (String excludeDirectoryFromBackup : Config.getInstance().getExcludeDirectoryFromBackup()) {

            try {

                File excludeDirectoryFromBackupFile = Paths.get(excludeDirectoryFromBackup).toFile().getCanonicalFile();

                if (excludeDirectoryFromBackupFile.equals(path.getCanonicalFile())) {
                    isExcludedDirectory = true;
                }

            } catch (SecurityException e) {
                Logger.getLogger().warn("Failed to copy file \"" + path.getAbsolutePath() + "\", no access", sender);
                Logger.getLogger().warn("BackupTask", e);
            } catch (Exception e) {
                Logger.getLogger().warn("Something went wrong while trying to copy file \"" + path.getAbsolutePath() + "\"", sender);
                Logger.getLogger().warn("BackupTask", e);
            }
        }

        return isExcludedDirectory;
    }

    public static ArrayList<LocalDateTime> getBackups() {

        ArrayList<LocalDateTime> backups = new ArrayList<>();
        for (File file : Objects.requireNonNull(new File(Config.getInstance().getBackupsFolder()).listFiles())) {
            try {
                backups.add(LocalDateTime.parse(file.getName().replace(".zip", ""), ru.dvdishka.backuper.backend.utils.Backup.dateTimeFormatter));
            } catch (Exception ignored) {}
        }
        return backups;
    }

    public static void returnFailure(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
        } catch (Exception ignored) {}
    }

    public static void returnFailure(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(Component.text(message).color(color));
        } catch (Exception ignored) {}
    }

    public static void returnSuccess(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message));
        } catch (Exception ignored) {}
    }

    public static void returnSuccess(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(color + message);
        } catch (Exception ignored) {}
    }

    public static void returnWarning(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message).color(TextColor.color(211, 145, 0)));
        } catch (Exception ignored) {}
    }


    public static void returnWarning(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(color + message);
        } catch (Exception ignored) {}
    }

    public static void sendMessage(String message, @NotNull CommandSender sender) {
        try {
            sender.sendMessage(message);
        } catch (Exception ignored) {}
    }

    public static void cancelButtonSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("block.anvil.place"), Sound.Source.NEUTRAL, 50, 1)).build());
        } catch (Exception ignored) {}
    }

    public static void normalButtonSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("ui.button.click"), Sound.Source.NEUTRAL, 50, 1)).build());
        } catch (Exception ignored) {}
    }

    public static void sendFramedMessage(Component message, CommandSender sender) {
        try {

            Component framedMessage = Component.empty();

            if (sender instanceof ConsoleCommandSender) {
                framedMessage = framedMessage
                        .append(Component.newline());
            }

            framedMessage = framedMessage
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x143E77)))
                    .append(Component.newline());

            framedMessage = framedMessage.append(message);

            framedMessage = framedMessage
                    .append(Component.newline())
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x143E77)));

            sender.sendMessage(framedMessage);

        } catch (Exception ignored) {}
    }
}
