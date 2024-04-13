package ru.dvdishka.backuper.backend.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.backend.common.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.collect.Comparators.min;

public class Config {

    private File configFile = null;

    private final String configVersion = "6.0";
    private long lastBackup = 0;
    private long lastChange = 0;

    private String backupsFolder = "plugins/Backuper/Backups";
    private List<String> addDirectoryToBackup = new ArrayList<>();
    private List<String> excludeDirectoryFromBackup = new ArrayList<>();
    private boolean fixedBackupTime = false;
    private boolean autoBackup = true;
    private int backupTime = -1;
    private int backupPeriod = 1440;
    private String afterBackup = "NOTHING";
    private boolean skipDuplicateBackup = true;
    private int backupsNumber = 0;
    private long backupsWeight = 0;
    private boolean zipArchive = true;
    private long alertTimeBeforeRestart = 60;
    private boolean betterLogging = false;
    private boolean notSetReadOnly = false;
    private boolean alertOnlyServerRestart = false;

    private static Config instance = null;

    public static Config getInstance() {

        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private Config() {}

    public synchronized void setConfigField(String path, Object value) {

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set(path, value);
        try {
            config.save(configFile);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to save config");
        }
    }

    public void updateLastChange() {
        this.lastChange = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

    public void updateLastBackup() {
        this.lastBackup = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

    public void load(File configFile, CommandSender sender) {

        this.configFile = configFile;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean noErrors = true;

        String configVersion = config.getString("configVersion");

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
        this.addDirectoryToBackup = config.getStringList("addDirectoryToBackup");
        this.excludeDirectoryFromBackup = config.getStringList("excludeDirectoryFromBackup");
        this.alertTimeBeforeRestart = config.getLong("alertTimeBeforeRestart", 60);
        this.notSetReadOnly = config.getBoolean("notSetReadOnly", false);
        this.alertOnlyServerRestart = config.getBoolean("alertOnlyServerRestart", false);

        if (this.alertTimeBeforeRestart >= this.backupPeriod * 60L) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("alertTimeBeforeRestart must be < backupPeriod * 60, using backupPeriod * 60 - 1 value...");
            this.alertTimeBeforeRestart = this.backupPeriod * 60L - 1L;
        }

        boolean isConfigFileOk = Objects.equals(configVersion, this.configVersion);

        List<String> configFields = List.of("backupTime", "backupPeriod", "afterBackup", "maxBackupsNumber",
                "maxBackupsWeight", "zipArchive", "betterLogging", "autoBackup", "lastBackup", "lastChange",
                "skipDuplicateBackup", "backupsFolder", "alertTimeBeforeRestart", "addDirectoryToBackup",
                "excludeDirectoryFromBackup", "notSetReadOnly", "alertOnlyServerRestart");

        for (String configField : configFields) {
            isConfigFileOk = min(isConfigFileOk, config.contains(configField));
        }

        if (!isConfigFileOk) {

            Logger.getLogger().warn("The config.yml file is damaged, repair...");
            Logger.getLogger().warn("If the plugin has just been updated, ignore this warning");

            if (!configFile.delete()) {
                Logger.getLogger().warn("Can not delete old config file!", sender);
                noErrors = false;
            }

            Utils.plugin.saveDefaultConfig();
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
            newConfig.set("alertTimeBeforeRestart", this.alertTimeBeforeRestart);
            newConfig.set("addDirectoryToBackup", this.addDirectoryToBackup);
            newConfig.set("excludeDirectoryFromBackup", this.excludeDirectoryFromBackup);
            newConfig.set("notSetReadOnly", this.notSetReadOnly);
            newConfig.set("alertOnlyServerRestart", this.alertOnlyServerRestart);

            try {

                newConfig.save(configFile);

            } catch (Exception e) {

                Logger.getLogger().warn("Can not save config file!", sender);
                Logger.getLogger().warn("Initialization", e);
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

    public List<String> getAddDirectoryToBackup() {
        return addDirectoryToBackup;
    }

    public List<String> getExcludeDirectoryFromBackup() {
        return excludeDirectoryFromBackup;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public long getAlertTimeBeforeRestart() {
        return alertTimeBeforeRestart;
    }

    public FileConfiguration getFileConfiguration() {
        return YamlConfiguration.loadConfiguration(configFile);
    }

    public boolean isNotSetReadOnly() {
        return notSetReadOnly;
    }

    public boolean isAlertOnlyServerRestart() {
        return alertOnlyServerRestart;
    }
}