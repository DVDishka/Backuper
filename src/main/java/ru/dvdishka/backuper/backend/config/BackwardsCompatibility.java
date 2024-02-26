package ru.dvdishka.backuper.backend.config;

import org.bukkit.configuration.file.FileConfiguration;

public class BackwardsCompatibility {

    public static void backupPeriodFromHoursToMinutes(FileConfiguration config) {

        double configVersion = config.getDouble("configVersion");
        if (configVersion >= 4.0) {
            return;
        }

        int backupPeriod = config.getInt("backupPeriod", 1440);
        backupPeriod *= 60;

        config.set("backupPeriod", backupPeriod);
    }

    public static void fixedBackupTimeToBackupTime(FileConfiguration config) {

        double configVersion = config.getDouble("configVersion");
        if (configVersion >= 4.0) {
            return;
        }

        boolean fixedBackupTime = config.getBoolean("fixedBackupTime", false);

        if (!fixedBackupTime) {
            config.set("backupTime", -1);
        }
        else {
            config.set("backupTime", config.getInt("firstBackupTime"));
        }
    }
}
