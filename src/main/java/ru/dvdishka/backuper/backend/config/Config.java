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

public class Config {

    private File configFile = null;

    private final String configVersion = "8.0";
    private long lastBackup = 0;
    private long lastChange = 0;

    private List<String> addDirectoryToBackup = new ArrayList<>();
    private List<String> excludeDirectoryFromBackup = new ArrayList<>();
    private boolean fixedBackupTime = false;
    private boolean autoBackup = true;
    private int backupTime = -1;
    private int backupPeriod = 1440;
    private String afterBackup = "NOTHING";
    private boolean skipDuplicateBackup = true;
    private long alertTimeBeforeRestart = 60;
    private boolean betterLogging = false;
    private boolean setWorldsReadOnly = false;
    private boolean alertOnlyServerRestart = true;

    private LocalConfig localConfig = new LocalConfig();
    private SftpConfig sftpConfig = new SftpConfig();

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

        BackwardsCompatibility.configBelow4(config);
        BackwardsCompatibility.configBelow8(config);

        this.backupTime = config.getInt("backup.backupTime", -1);
        this.backupPeriod = config.getInt("backup.backupPeriod", 1440);
        this.afterBackup = config.getString("backup.afterBackup", "NOTHING").toUpperCase();
        this.setWorldsReadOnly = config.getBoolean("backup.setWorldsReadOnly", false);
        this.autoBackup = config.getBoolean("backup.autoBackup", true);
        this.skipDuplicateBackup = config.getBoolean("backup.skipDuplicateBackup", true);

        this.localConfig.enabled = config.getBoolean("local.enabled", true);
        this.localConfig.backupsNumber = config.getInt("local.maxBackupsNumber", 0);
        this.localConfig.backupsWeight = config.getLong("local.maxBackupsWeight", 0) * 1_048_576L;
        this.localConfig.zipArchive = config.getBoolean("local.zipArchive", true);
        this.localConfig.backupsFolder = config.getString("local.backupsFolder", "plugins/Backuper/Backups");
        this.localConfig.zipCompressionLevel = config.getInt("local.zipCompressionLevel", 5);

        this.sftpConfig.enabled = config.getBoolean("sftp.enabled", false);
        this.sftpConfig.backupsFolder = config.getString("sftp.backupsFolder", "");
        this.sftpConfig.authType = config.getString("sftp.authType", "password");
        this.sftpConfig.username = config.getString("sftp.username", "");
        this.sftpConfig.password = config.getString("sftp.password", "");
        this.sftpConfig.address = config.getString("sftp.address", "");
        this.sftpConfig.keyFilePath = config.getString("sftp.keyFilePath", "");
        this.sftpConfig.port = config.getInt("sftp.port", 22);
        this.sftpConfig.useKnownHostsFile = config.getString("sftp.useKnownHostsFile", "false");
        this.sftpConfig.knownHostsFilePath = config.getString("sftp.knownHostsFilePath", "");
        this.sftpConfig.pathSeparatorSymbol = config.getString("sftp.pathSeparatorSymbol", "/");

        this.betterLogging = config.getBoolean("server.betterLogging", false);
        this.fixedBackupTime = this.backupTime > -1;
        this.addDirectoryToBackup = config.getStringList("backup.addDirectoryToBackup");
        this.excludeDirectoryFromBackup = config.getStringList("backup.excludeDirectoryFromBackup");
        this.alertTimeBeforeRestart = config.getLong("server.alertTimeBeforeRestart", 60);
        this.alertOnlyServerRestart = config.getBoolean("server.alertOnlyServerRestart", true);

        this.lastBackup = config.getLong("lastBackup", 0);
        this.lastChange = config.getLong("lastChange", 0);

        if (this.backupTime < -1) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("backupTime must be >= -1, using default -1 value...");
            this.backupTime = -1;
        }

        if (this.alertTimeBeforeRestart >= this.backupPeriod * 60L) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("alertTimeBeforeRestart must be < backupPeriod * 60, using backupPeriod * 60 - 1 value...");
            this.alertTimeBeforeRestart = this.backupPeriod * 60L - 1L;
        }

        if (this.backupPeriod <= 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("Backup period must be > 0, using default 1440 value...");
            this.backupPeriod = 1440;
        }

        if (this.localConfig.backupsNumber < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("Backups number must be >= 0, using default 0 value...");
            this.localConfig.backupsNumber = 0;
        }

        if (this.localConfig.backupsWeight < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("Backups weight must be >= 0, using default 0 value...");
            this.localConfig.backupsWeight = 0;
        }

        if (this.localConfig.zipCompressionLevel > 9 || this.localConfig.zipCompressionLevel < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            if (this.localConfig.zipCompressionLevel < 0) {
                Logger.getLogger().warn("ZipCompressionLevel must be >= 0, using 0 value...");
                this.localConfig.zipCompressionLevel = 0;
            }
            if (this.localConfig.zipCompressionLevel > 9) {
                Logger.getLogger().warn("ZipCompressionLevel must be <= 9, using 9 value...");
                this.localConfig.zipCompressionLevel = 9;
            }
        }

        boolean isConfigFileOk = Objects.equals(configVersion, this.configVersion);

        List<String> configFields = List.of("backup.backupTime", "backup.backupPeriod", "backup.afterBackup", "local.maxBackupsNumber",
                "local.maxBackupsWeight", "local.zipArchive", "server.betterLogging", "backup.autoBackup", "lastBackup", "lastChange",
                "backup.skipDuplicateBackup", "local.backupsFolder", "server.alertTimeBeforeRestart", "backup.addDirectoryToBackup",
                "backup.excludeDirectoryFromBackup", "backup.setWorldsReadOnly", "server.alertOnlyServerRestart", "sftp.enabled",
                "sftp.backupsFolder", "sftp.authType", "sftp.username", "sftp.password", "sftp.keyFilePath", "sftp.address",
                "sftp.port", "sftp.useKnownHostsFile", "sftp.knownHostsFilePath", "local.enabled", "sftp.pathSeparatorSymbol", "local.zipCompressionLevel");

        for (String configField : configFields) {
            if (isConfigFileOk && !config.contains(configField)) {
                isConfigFileOk = false;
            }
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

            newConfig.set("backup.backupTime", this.backupTime);
            newConfig.set("backup.backupPeriod", this.backupPeriod);
            newConfig.set("backup.afterBackup", this.afterBackup);
            newConfig.set("backup.autoBackup", this.autoBackup);
            newConfig.set("backup.skipDuplicateBackup", this.skipDuplicateBackup);
            newConfig.set("backup.addDirectoryToBackup", this.addDirectoryToBackup);
            newConfig.set("backup.excludeDirectoryFromBackup", this.excludeDirectoryFromBackup);
            newConfig.set("backup.setWorldsReadOnly", this.setWorldsReadOnly);

            newConfig.set("local.enabled", this.localConfig.enabled);
            newConfig.set("local.maxBackupsNumber", this.localConfig.backupsNumber);
            newConfig.set("local.maxBackupsWeight", this.localConfig.backupsWeight / 1_048_576L);
            newConfig.set("local.zipArchive", this.localConfig.zipArchive);
            newConfig.set("local.backupsFolder", this.localConfig.backupsFolder);
            newConfig.set("local.zipCompressionLevel", this.localConfig.zipCompressionLevel);

            newConfig.set("sftp.enabled", this.sftpConfig.enabled);
            newConfig.set("sftp.password", this.sftpConfig.password);
            newConfig.set("sftp.username", this.sftpConfig.username);
            newConfig.set("sftp.backupsFolder", this.sftpConfig.backupsFolder);
            newConfig.set("sftp.authType", this.sftpConfig.authType);
            newConfig.set("sftp.keyFilePath", this.sftpConfig.keyFilePath);
            newConfig.set("sftp.address", this.sftpConfig.address);
            newConfig.set("sftp.port", this.sftpConfig.port);
            newConfig.set("sftp.useKnownHostsFile", this.sftpConfig.useKnownHostsFile);
            newConfig.set("sftp.knownHostsFilePath", this.sftpConfig.knownHostsFilePath);
            newConfig.set("sftp.pathSeparatorSymbol", this.sftpConfig.pathSeparatorSymbol);

            newConfig.set("lastBackup", this.lastBackup);
            newConfig.set("lastChange", this.lastChange);

            newConfig.set("server.betterLogging", this.betterLogging);
            newConfig.set("server.alertTimeBeforeRestart", this.alertTimeBeforeRestart);
            newConfig.set("server.alertOnlyServerRestart", this.alertOnlyServerRestart);

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

    public int getBackupPeriod() {
        return backupPeriod;
    }

    public int getBackupTime() {
        return backupTime;
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

    public boolean isSetWorldsReadOnly() {
        return setWorldsReadOnly;
    }

    public boolean isAlertOnlyServerRestart() {
        return alertOnlyServerRestart;
    }

    public LocalConfig getLocalConfig() {
        return localConfig;
    }

    public SftpConfig getSftpConfig() {
        return sftpConfig;
    }
}