package ru.dvdishka.backuper.backend.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import ru.dvdishka.backuper.Backuper;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class ConfigBackwardsCompatibility {

    public static void configBelow4(FileConfiguration config) {

        double configVersion = config.getDouble("configVersion");
        if (configVersion >= 4.0) {
            return;
        }

        int backupPeriod = config.getInt("backupPeriod", 1440);
        backupPeriod *= 60;
        config.set("backupPeriod", backupPeriod);

        boolean fixedBackupTime = config.getBoolean("fixedBackupTime", false);
        if (!fixedBackupTime) {
            config.set("backupTime", -1);
        } else {
            config.set("backupTime", config.getInt("firstBackupTime"));
        }
    }

    public static void configBelow8(FileConfiguration config) {

        double configVersion = config.getDouble("configVersion");
        if (configVersion >= 8.0) {
            return;
        }

        // LOCAL SECTION
        String localBackupsFolder = config.getString("backupsFolder", "plugins/Backuper/Backups");
        int localMaxBackupsNumber = config.getInt("maxBackupsNumber", 0);
        long localMaxBackupsWeight = config.getLong("maxBackupsWeight", 0L);
        boolean localZipArchive = config.getBoolean("zipArchive", true);

        // BACKUP SECTION
        boolean autoBackup = config.getBoolean("autoBackup", true);
        List<String> addDirectoryToBackup = config.getStringList("addDirectoryToBackup");
        List<String> excludeDirectoryFromBackup = config.getStringList("excludeDirectoryFromBackup");
        int backupPeriod = config.getInt("backupPeriod", 1440);
        int backupTime = config.getInt("backupTime", -1);
        boolean skipDuplicateBackup = config.getBoolean("skipDuplicateBackup", true);
        String afterBackup = config.getString("afterBackup", "NOTHING");
        boolean setWorldsReadOnly = config.getBoolean("setWorldsReadOnly", false);

        // SERVER SECTION
        long alertTimeBeforeRestart = config.getLong("alertTimeBeforeRestart", 60L);
        boolean alertOnlyServerRestart = config.getBoolean("alertOnlyServerRestart", true);
        boolean betterLogging = config.getBoolean("betterLogging", false);

        config.set("local.backupsFolder", localBackupsFolder);
        config.set("local.maxBackupsNumber", localMaxBackupsNumber);
        config.set("local.maxBackupsWeight", localMaxBackupsWeight);
        config.set("local.zipArchive", localZipArchive);

        config.set("backup.autoBackup", autoBackup);
        config.set("backup.addDirectoryToBackup", addDirectoryToBackup);
        config.set("backup.excludeDirectoryFromBackup", excludeDirectoryFromBackup);
        config.set("backup.backupPeriod", backupPeriod);
        config.set("backup.backupTime", backupTime);
        config.set("backup.skipDuplicateBackup", skipDuplicateBackup);
        config.set("backup.afterBackup", afterBackup);
        config.set("backup.setWorldsReadOnly", setWorldsReadOnly);

        config.set("server.alertTimeBeforeRestart", alertTimeBeforeRestart);
        config.set("server.alertOnlyServerRestart", alertOnlyServerRestart);
        config.set("server.betterLogging", betterLogging);
    }

    public static void configBelow13(FileConfiguration config) { // Migrating from Time/Period format to Cron

        double configVersion = config.getDouble("configVersion");
        if (configVersion >= 13.0) {
            return;
        }

        int backupTime = config.getInt("backup.backupTime", -1);

        if (backupTime < -1 || backupTime > 23) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("backupTime must be >= -1, using default -1 value...");
            backupTime = -1;
        }

        String cron;
        if (backupTime != -1) {
            cron = "0 0 " + backupTime + " 1/1 * ? *";
        } else {
            cron = "0 0 0 1/1 * ? *";
            Backuper.getInstance().getLogManager().warn("The format of the auto-backup schedule definition has changed! Please go to Backuper's config.yml and define the schedule using the new format! Your old schedule will not work now!");
        }

        config.set("backup.autoBackupCron", cron);
    }

    public static void unifyBackupNameFormat(CommandSender sender) {

        try {

            DateTimeFormatter oldUnixDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            DateTimeFormatter oldWindowsDateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH;mm;ss");

            File backupsDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

            if (!backupsDir.exists() || backupsDir.listFiles() == null) {
                Backuper.getInstance().getLogManager().warn("Wrong local.backupFolder config field value", sender);
                throw new RuntimeException();
            }

            for (File file : Objects.requireNonNull(backupsDir.listFiles())) {

                LocalDateTime backupLocalDateTime = null;

                try {
                    backupLocalDateTime = LocalDateTime.parse(file.getName().replace(".zip", ""), oldUnixDateTimeFormatter);
                } catch (Exception ignored) {
                }

                try {
                    backupLocalDateTime = LocalDateTime.parse(file.getName().replace(".zip", ""), oldWindowsDateTimeFormatter);
                } catch (Exception ignored) {
                }

                if (backupLocalDateTime != null) {

                    boolean isZip = file.getName().endsWith(".zip");

                    String newFileName = Config.getInstance().getDateTimeFormatter().format(backupLocalDateTime);
                    if (isZip) {
                        newFileName += ".zip";
                    }

                    File newFile = new File(backupsDir, newFileName);

                    if (!file.renameTo(newFile)) {
                        Backuper.getInstance().getLogManager().warn("Failed to reformat backup to new unified format " + newFile.getAbsolutePath() + " (it will be unavailable)", sender);
                    }
                }
            }

        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to unify backup name format", sender);
            Backuper.getInstance().getLogManager().warn(e);
        }
    }
}
