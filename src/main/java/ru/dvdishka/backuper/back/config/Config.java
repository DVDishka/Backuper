package ru.dvdishka.backuper.back.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.common.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class Config {

    private File configFile = null;

    private final String configVersion = "5.0";
    private long lastBackup = 0;
    private long lastChange = 0;

    private String backupsFolder = "plugins/Backuper/Backups";
    private boolean fixedBackupTime = false;
    private boolean autoBackup = true;
    private int backupTime = -1;
    private int backupPeriod = 1440;
    private String afterBackup = "NOTHING";
    private boolean skipDuplicateBackup = true;
    private int backupsNumber = 0;
    private long backupsWeight = 0;
    private boolean zipArchive = true;
    private boolean betterLogging = false;

    private static Config instance = null;

    public static Config getInstance() {

        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private Config() {}

    private void setConfigField(String path, Object value) {

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set(path, value);
        try {
            config.save(configFile);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to set config field \"" + path + "\" to " + value);
        }
    }

    public void updateLastChange() {
        this.lastChange = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        setConfigField("lastChange", lastChange);
    }

    public void updateLastBackup() {
        this.lastBackup = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        setConfigField("lastBackup", lastBackup);
    }

    public void load(File configFile, CommandSender sender) {

        this.configFile = configFile;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean noErrors = true;

        String configVersion = config.getString("configVersion");
        assert configVersion != null;

        BackwardsCompatibility.backupPeriodFromHoursToMinutes(config);
        BackwardsCompatibility.fixedBackupTimeToBackupTime(config);

        this.backupTime = config.getInt("backupTime", -1);
        this.backupPeriod = config.getInt("backupPeriod", 1440);
        this.afterBackup = config.getString("afterBackup", "NOTHING").toUpperCase();
        this.backupsNumber = config.getInt("maxBackupsNumber", 7);
        this.backupsWeight = config.getLong("maxBackupsWeight", 0) * 1_048_576L;
        this.zipArchive = config.getBoolean("zipArchive", true);
        this.betterLogging = config.getBoolean("betterLogging", false);
        this.autoBackup = config.getBoolean("autoBackup", true);
        this.lastBackup = config.getLong("lastBackup", 0);
        this.lastChange = config.getLong("lastChange", 0);
        this.skipDuplicateBackup = config.getBoolean("skipDuplicateBackup", true);
        this.fixedBackupTime = this.backupTime > -1;
        this.backupsFolder = config.getString("backupsFolder", "plugins/Backuper/Backups");

        boolean isConfigFileOk = configVersion.equals(this.configVersion);

        if (!config.contains("backupTime")) {
            isConfigFileOk = false;
        }
        if (!config.contains("backupPeriod")) {
            isConfigFileOk = false;
        }
        if (!config.contains("afterBackup")) {
            isConfigFileOk = false;
        }
        if (!config.contains("maxBackupsNumber")) {
            isConfigFileOk = false;
        }
        if (!config.contains("maxBackupsWeight")) {
            isConfigFileOk = false;
        }
        if (!config.contains("zipArchive")) {
            isConfigFileOk = false;
        }
        if (!config.contains("betterLogging")) {
            isConfigFileOk = false;
        }
        if (!config.contains("autoBackup")) {
            isConfigFileOk = false;
        }
        if (!config.contains("lastBackup")) {
            isConfigFileOk = false;
        }
        if (!config.contains("lastChange")) {
            isConfigFileOk = false;
        }
        if (!config.contains("backupsFolder")) {
            isConfigFileOk = false;
        }
        if (!config.contains("skipDuplicateBackup")) {
            isConfigFileOk = false;
        }

        if (!isConfigFileOk) {

            if (!configFile.delete()) {
                Logger.getLogger().warn("Can not delete old config file!", sender);
                noErrors = false;
            }

            Common.plugin.saveDefaultConfig();
            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(configFile);

            newConfig.set("backupTime", this.backupTime);
            newConfig.set("backupPeriod", this.backupPeriod);
            newConfig.set("afterBackup", this.afterBackup);
            newConfig.set("maxBackupsNumber", this.backupsNumber);
            newConfig.set("maxBackupsWeight", this.backupsWeight / 1_048_576L);
            newConfig.set("zipArchive", this.zipArchive);
            newConfig.set("betterLogging", this.betterLogging);
            newConfig.set("autoBackup", this.autoBackup);
            newConfig.set("lastBackup", this.lastBackup);
            newConfig.set("lastChange", this.lastChange);
            newConfig.set("backupsFolder", this.backupsFolder);
            newConfig.set("skipDuplicateBackup", this.skipDuplicateBackup);

            try {

                newConfig.save(configFile);

            } catch (Exception e) {

                Logger.getLogger().warn("Can not save config file!", sender);
                Logger.getLogger().devWarn("Initialization", e);
                noErrors = false;
            }
        }

        if (sender != null && noErrors) {
            Logger.getLogger().success("Config has been reloaded successfully!", sender);
        }
        if (sender != null && !noErrors) {
            Logger.getLogger().log("Config has been reloaded with errors!", sender);
        }
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public boolean isBetterLogging() {
        return betterLogging;
    }

    public boolean isFixedBackupTime() {
        return fixedBackupTime;
    }

    public boolean isSkipDuplicateBackup() {
        return skipDuplicateBackup;
    }

    public boolean isZipArchive() {
        return zipArchive;
    }

    public int getBackupPeriod() {
        return backupPeriod;
    }

    public int getBackupsNumber() {
        return backupsNumber;
    }

    public int getBackupTime() {
        return backupTime;
    }

    public long getBackupsWeight() {
        return backupsWeight;
    }

    public long getLastBackup() {
        return lastBackup;
    }

    public long getLastChange() {
        return lastChange;
    }

    public String getAfterBackup() {
        return afterBackup;
    }

    public String getBackupsFolder() {
        return backupsFolder;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public FileConfiguration getFileConfiguration() {
        return YamlConfiguration.loadConfiguration(configFile);
    }
}