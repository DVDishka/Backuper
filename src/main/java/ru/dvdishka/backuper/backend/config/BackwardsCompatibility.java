package ru.dvdishka.backuper.backend.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class BackwardsCompatibility {

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
        }
        else {
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
}
