package ru.dvdishka.backuper.backend.config;

import dev.jorel.commandapi.CommandAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

public class Config {

    // Fields are defined in `load()`
    // Defining fields when initializing while also defining with defaults later
    // hides bugs that may exist with reading the config.
    private File configFile;

    private final String configVersion = "9.0";
    private long lastBackup;
    private long lastChange;

    private List<String> addDirectoryToBackup;
    private List<String> excludeDirectoryFromBackup;
    private boolean fixedBackupTime;
    private boolean autoBackup;
    private int backupTime;
    private int backupPeriod;
    private String afterBackup;
    private boolean skipDuplicateBackup;
    private long alertTimeBeforeRestart;
    private boolean betterLogging;
    private boolean setWorldsReadOnly;
    private boolean alertOnlyServerRestart;
    private boolean checkUpdates;
    private boolean deleteBrokenBackups;
    private String backupFileNameFormat;
    private DateTimeFormatter dateTimeFormatter;
    private File sizeCacheFile;
    private String alertBackupMessage;
    private String alertBackupRestartMessage;

    private final LocalConfig localConfig = new LocalConfig();
    private final SftpConfig sftpConfig = new SftpConfig();
    private final FtpConfig ftpConfig = new FtpConfig();
    private final GoogleDriveConfig googleDriveConfig = new GoogleDriveConfig();

    private static Config instance = null;

    public static Config getInstance() {

        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private Config() {
    }

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

        Logger.getLogger().devLog("loading config...", sender);

        this.configFile = configFile;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean noErrors = true;

        String configVersion = config.getString("configVersion");

        BackwardsCompatibility.configBelow4(config);
        BackwardsCompatibility.configBelow8(config);

        boolean isConfigFileOk = true;

        this.backupTime = config.getInt("backup.backupTime", -1);
        this.backupFileNameFormat = config.getString("backup.backupFileNameFormat", "dd-MM-yyyy HH-mm-ss");
        this.backupPeriod = config.getInt("backup.backupPeriod", 1440);
        this.afterBackup = config.getString("backup.afterBackup", "NOTHING").toUpperCase();
        this.setWorldsReadOnly = config.getBoolean("backup.setWorldsReadOnly", false);
        this.autoBackup = config.getBoolean("backup.autoBackup", true);
        this.skipDuplicateBackup = config.getBoolean("backup.skipDuplicateBackup", true);
        this.deleteBrokenBackups = config.getBoolean("backup.deleteBrokenBackups", true);

        this.localConfig.enabled = config.getBoolean("local.enabled", true);
        this.localConfig.autoBackup = config.getBoolean("local.autoBackup", true);
        this.localConfig.backupsNumber = config.getInt("local.maxBackupsNumber", 0);
        this.localConfig.backupsWeight = config.getLong("local.maxBackupsWeight", 0) * 1_048_576L;
        this.localConfig.zipArchive = config.getBoolean("local.zipArchive", true);
        this.localConfig.backupsFolder = config.getString("local.backupsFolder", "plugins/Backuper/Backups");
        this.localConfig.zipCompressionLevel = config.getInt("local.zipCompressionLevel", 5);

        this.ftpConfig.enabled = config.getBoolean("ftp.enabled", false);
        this.ftpConfig.autoBackup = config.getBoolean("ftp.autoBackup", true);
        this.ftpConfig.backupsFolder = config.getString("ftp.backupsFolder", "");
        this.ftpConfig.pathSeparatorSymbol = config.getString("ftp.pathSeparatorSymbol", "/");
        this.ftpConfig.backupsNumber = config.getInt("ftp.maxBackupsNumber", 0);
        this.ftpConfig.backupsWeight = config.getLong("ftp.maxBackupsWeight", 0) * 1_048_576L;
        this.ftpConfig.zipArchive = config.getBoolean("ftp.zipArchive", true);
        this.ftpConfig.zipCompressionLevel = config.getInt("ftp.zipCompressionLevel", 5);
        this.ftpConfig.address = config.getString("ftp.auth.address", "");
        this.ftpConfig.port = config.getInt("ftp.auth.port", 21);
        this.ftpConfig.username = config.getString("ftp.auth.username", "");
        this.ftpConfig.password = config.getString("ftp.auth.password", "");

        this.sftpConfig.enabled = config.getBoolean("sftp.enabled", false);
        this.sftpConfig.autoBackup = config.getBoolean("sftp.autoBackup", true);
        this.sftpConfig.backupsFolder = config.getString("sftp.backupsFolder", "");
        this.sftpConfig.pathSeparatorSymbol = config.getString("sftp.pathSeparatorSymbol", "/");
        this.sftpConfig.backupsNumber = config.getInt("sftp.maxBackupsNumber", 0);
        this.sftpConfig.backupsWeight = config.getLong("sftp.maxBackupsWeight", 0) * 1_048_576L;
        this.sftpConfig.keyFilePath = config.getString("sftp.auth.keyFilePath", "");
        this.sftpConfig.authType = config.getString("sftp.auth.authType", "password");
        this.sftpConfig.username = config.getString("sftp.auth.username", "");
        this.sftpConfig.password = config.getString("sftp.auth.password", "");
        this.sftpConfig.address = config.getString("sftp.auth.address", "");
        this.sftpConfig.port = config.getInt("sftp.auth.port", 22);
        this.sftpConfig.useKnownHostsFile = config.getString("sftp.auth.useKnownHostsFile", "false");
        this.sftpConfig.knownHostsFilePath = config.getString("sftp.auth.knownHostsFilePath", "");

        this.googleDriveConfig.enabled = config.getBoolean("googleDrive.enabled", false);
        this.googleDriveConfig.autoBackup = config.getBoolean("googleDrive.autoBackup", true);
        this.googleDriveConfig.backupsFolderId = config.getString("googleDrive.backupsFolderId", "");
        String googleDriveTokenFolder = config.getString("googleDrive.auth.tokenFolderPath", "plugins/Backuper/GoogleDrive/tokens");
        this.googleDriveConfig.tokenFolder = new File(googleDriveTokenFolder);
        this.googleDriveConfig.createBackuperFolder = config.getBoolean("googleDrive.createBackuperFolder", true);
        this.googleDriveConfig.backupsNumber = config.getInt("googleDrive.maxBackupsNumber", 0);
        this.googleDriveConfig.backupsWeight = config.getLong("googleDrive.maxBackupsWeight", 0) * 1_048_576L;

        this.alertBackupMessage = config.getString("server.alertBackupMessage", "Server will be backed up in %d second(s)");
        this.alertBackupRestartMessage = config.getString("server.alertBackupRestartMessage", "Server will be backed up and restarted in %d second(s)");
        this.sizeCacheFile = new File(config.getString("server.sizeCacheFile", "./plugins/Backuper/sizeCache.json"));
        this.betterLogging = config.getBoolean("server.betterLogging", false);
        this.fixedBackupTime = this.backupTime > -1;
        this.addDirectoryToBackup = config.getStringList("backup.addDirectoryToBackup");
        this.excludeDirectoryFromBackup = config.getStringList("backup.excludeDirectoryFromBackup");
        this.alertTimeBeforeRestart = config.getLong("server.alertTimeBeforeRestart", 60);
        this.alertOnlyServerRestart = config.getBoolean("server.alertOnlyServerRestart", true);
        this.checkUpdates = config.getBoolean("server.checkUpdates", true);

        this.lastBackup = config.getLong("lastBackup", 0);
        this.lastChange = config.getLong("lastChange", 0);

        try {
            this.dateTimeFormatter = DateTimeFormatter.ofPattern(backupFileNameFormat);
            LocalDateTime localDateTime = LocalDateTime.parse(LocalDateTime.now().format(dateTimeFormatter), dateTimeFormatter);
        } catch (Exception e) {
            Logger.getLogger().warn("Wrong backupFileNameFormat format: \"" + backupFileNameFormat + "\", using default \"dd-MM-yyyy HH-mm-ss\" value...");
            Logger.getLogger().warn(this.getClass(), e);
            isConfigFileOk = false;
            backupFileNameFormat = "dd-MM-yyyy HH-mm-ss";
        }

        if (this.backupTime < -1) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("backupTime must be >= -1, using default -1 value...");
            this.backupTime = -1;
        }

        if (this.alertTimeBeforeRestart >= this.backupPeriod * 60L && this.backupPeriod != -1) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("alertTimeBeforeRestart must be < backupPeriod * 60, using backupPeriod * 60 - 1 value...");
            this.alertTimeBeforeRestart = this.backupPeriod * 60L - 1L;
        }

        if (this.backupPeriod <= 0 && this.backupPeriod != -1) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("backup.backupPeriod must be > 0, using default 1440 value...");
            this.backupPeriod = 1440;
        }

        if (this.localConfig.backupsNumber < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("local.maxBackupsNumber must be >= 0, using default 0 value...");
            this.localConfig.backupsNumber = 0;
        }

        if (this.localConfig.backupsWeight < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("local.maxBackupsWeight must be >= 0, using default 0 value...");
            this.localConfig.backupsWeight = 0;
        }

        if (this.sftpConfig.backupsNumber < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("sftp.maxBackupsNumber must be >= 0, using default 0 value...");
            this.sftpConfig.backupsNumber = 0;
        }

        if (this.sftpConfig.backupsWeight < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            Logger.getLogger().warn("sftp.maxBackupsWeight must be >= 0, using default 0 value...");
            this.sftpConfig.backupsWeight = 0;
        }

        if (this.localConfig.zipCompressionLevel > 9 || this.localConfig.zipCompressionLevel < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            if (this.localConfig.zipCompressionLevel < 0) {
                Logger.getLogger().warn("local.zipCompressionLevel must be >= 0, using 0 value...");
                this.localConfig.zipCompressionLevel = 0;
            }
            if (this.localConfig.zipCompressionLevel > 9) {
                Logger.getLogger().warn("local.zipCompressionLevel must be <= 9, using 9 value...");
                this.localConfig.zipCompressionLevel = 9;
            }
        }

        if (this.ftpConfig.zipCompressionLevel > 9 || this.ftpConfig.zipCompressionLevel < 0) {
            Logger.getLogger().warn("Failed to load config value!");
            if (this.ftpConfig.zipCompressionLevel < 0) {
                Logger.getLogger().warn("ftp.zipCompressionLevel must be >= 0, using 0 value...");
                this.ftpConfig.zipCompressionLevel = 0;
            }
            if (this.ftpConfig.zipCompressionLevel > 9) {
                Logger.getLogger().warn("ftp.zipCompressionLevel must be <= 9, using 9 value...");
                this.ftpConfig.zipCompressionLevel = 9;
            }
        }

        isConfigFileOk = isConfigFileOk && Objects.equals(configVersion, this.configVersion);

        List<String> configFields = List.of("backup.backupTime", "backup.backupPeriod", "backup.afterBackup", "local.maxBackupsNumber",
                "local.maxBackupsWeight", "local.zipArchive", "server.betterLogging", "backup.autoBackup", "lastBackup", "lastChange",
                "backup.skipDuplicateBackup", "local.backupsFolder", "server.alertTimeBeforeRestart", "backup.addDirectoryToBackup",
                "backup.excludeDirectoryFromBackup", "backup.setWorldsReadOnly", "server.alertOnlyServerRestart", "sftp.enabled",
                "sftp.backupsFolder", "sftp.auth.authType", "sftp.auth.username", "sftp.auth.password", "sftp.auth.keyFilePath", "sftp.auth.address",
                "sftp.auth.port", "sftp.auth.useKnownHostsFile", "sftp.auth.knownHostsFilePath", "local.enabled", "sftp.pathSeparatorSymbol",
                "local.zipCompressionLevel", "sftp.maxBackupsNumber", "sftp.maxBackupsWeight", "ftp.backupsFolder", "ftp.auth.address", "ftp.auth.port",
                "ftp.pathSeparatorSymbol", "ftp.auth.password", "ftp.auth.username", "ftp.enabled", "ftp.maxBackupsNumber", "ftp.maxBackupsWeight",
                "ftp.zipArchive", "ftp.zipCompressionLevel", "server.checkUpdates", "local.autoBackup", "ftp.autoBackup", "sftp.autoBackup",
                "backup.deleteBrokenBackups", "backup.backupFileNameFormat", "googleDrive.enabled", "googleDrive.autoBackup",
                "googleDrive.auth.tokenFolderPath", "googleDrive.backupsFolderId", "googleDrive.createBackuperFolder",
                "googleDrive.maxBackupsWeight", "googleDrive.maxBackupsNumber", "server.sizeCacheFile", "server.alertBackupMessage", "server.alertBackupRestartMessage");

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
            newConfig.set("backup.backupFileNameFormat", this.backupFileNameFormat);
            newConfig.set("backup.backupPeriod", this.backupPeriod);
            newConfig.set("backup.afterBackup", this.afterBackup);
            newConfig.set("backup.autoBackup", this.autoBackup);
            newConfig.set("backup.skipDuplicateBackup", this.skipDuplicateBackup);
            newConfig.set("backup.addDirectoryToBackup", this.addDirectoryToBackup);
            newConfig.set("backup.excludeDirectoryFromBackup", this.excludeDirectoryFromBackup);
            newConfig.set("backup.setWorldsReadOnly", this.setWorldsReadOnly);
            newConfig.set("backup.deleteBrokenBackups", this.deleteBrokenBackups);

            newConfig.set("local.enabled", this.localConfig.enabled);
            newConfig.set("local.autoBackup", this.localConfig.autoBackup);
            newConfig.set("local.maxBackupsNumber", this.localConfig.backupsNumber);
            newConfig.set("local.maxBackupsWeight", this.localConfig.backupsWeight / 1_048_576L);
            newConfig.set("local.zipArchive", this.localConfig.zipArchive);
            newConfig.set("local.backupsFolder", this.localConfig.backupsFolder);
            newConfig.set("local.zipCompressionLevel", this.localConfig.zipCompressionLevel);

            newConfig.set("ftp.enabled", this.ftpConfig.enabled);
            newConfig.set("ftp.autoBackup", this.ftpConfig.autoBackup);
            newConfig.set("ftp.backupsFolder", this.ftpConfig.backupsFolder);
            newConfig.set("ftp.pathSeparatorSymbol", this.ftpConfig.pathSeparatorSymbol);
            newConfig.set("ftp.maxBackupsNumber", this.ftpConfig.backupsNumber);
            newConfig.set("ftp.maxBackupsWeight", this.ftpConfig.backupsWeight / 1_048_576L);
            newConfig.set("ftp.zipArchive", true);
            newConfig.set("ftp.zipCompressionLevel", 5);
            newConfig.set("ftp.auth.address", this.ftpConfig.address);
            newConfig.set("ftp.auth.port", this.ftpConfig.port);
            newConfig.set("ftp.auth.username", this.ftpConfig.username);
            newConfig.set("ftp.auth.password", this.ftpConfig.password);

            newConfig.set("sftp.pathSeparatorSymbol", this.sftpConfig.pathSeparatorSymbol);
            newConfig.set("sftp.maxBackupsNumber", this.sftpConfig.backupsNumber);
            newConfig.set("sftp.maxBackupsWeight", this.sftpConfig.backupsWeight / 1_048_576L);
            newConfig.set("sftp.enabled", this.sftpConfig.enabled);
            newConfig.set("sftp.autoBackup", this.sftpConfig.autoBackup);
            newConfig.set("sftp.backupsFolder", this.sftpConfig.backupsFolder);
            newConfig.set("sftp.auth.password", this.sftpConfig.password);
            newConfig.set("sftp.auth.username", this.sftpConfig.username);
            newConfig.set("sftp.auth.authType", this.sftpConfig.authType);
            newConfig.set("sftp.auth.keyFilePath", this.sftpConfig.keyFilePath);
            newConfig.set("sftp.auth.address", this.sftpConfig.address);
            newConfig.set("sftp.auth.port", this.sftpConfig.port);
            newConfig.set("sftp.auth.useKnownHostsFile", this.sftpConfig.useKnownHostsFile);
            newConfig.set("sftp.auth.knownHostsFilePath", this.sftpConfig.knownHostsFilePath);

            newConfig.set("googleDrive.enabled", this.googleDriveConfig.enabled);
            newConfig.set("googleDrive.autoBackup", this.googleDriveConfig.autoBackup);
            newConfig.set("googleDrive.backupsFolderId", this.googleDriveConfig.backupsFolderId);
            newConfig.set("googleDrive.auth.tokenFolderPath", googleDriveTokenFolder);
            newConfig.set("googleDrive.createBackuperFolder", this.googleDriveConfig.createBackuperFolder);
            newConfig.set("googleDrive.maxBackupsWeight", this.googleDriveConfig.backupsWeight / 1_048_576L);
            newConfig.set("googleDrive.maxBackupsNumber", this.googleDriveConfig.backupsNumber);

            newConfig.set("lastBackup", this.lastBackup);
            newConfig.set("lastChange", this.lastChange);

            newConfig.set("server.sizeCacheFile", this.sizeCacheFile.getPath());
            newConfig.set("server.betterLogging", this.betterLogging);
            newConfig.set("server.alertTimeBeforeRestart", this.alertTimeBeforeRestart);
            newConfig.set("server.alertOnlyServerRestart", this.alertOnlyServerRestart);
            newConfig.set("server.alertBackupMessage", this.alertBackupMessage);
            newConfig.set("server.alertBackupRestartMessage", this.alertBackupRestartMessage);
            newConfig.set("server.checkUpdates", this.checkUpdates);

            try {

                newConfig.save(configFile);

            } catch (Exception e) {

                Logger.getLogger().warn("Can not save config file!", sender);
                Logger.getLogger().warn("Initialization", e);
                noErrors = false;
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            CommandAPI.updateRequirements(player);
        }

        if (noErrors) {
            Logger.getLogger().devLog("Config has been loaded successfully!", sender);
        }
        if (!noErrors) {
            Logger.getLogger().warn("Config has been loaded with errors!", sender);
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

    public boolean isCheckUpdates() {
        return checkUpdates;
    }

    public LocalConfig getLocalConfig() {
        return localConfig;
    }

    public FtpConfig getFtpConfig() {
        return ftpConfig;
    }

    public SftpConfig getSftpConfig() {
        return sftpConfig;
    }

    public GoogleDriveConfig getGoogleDriveConfig() {
        return googleDriveConfig;
    }

    public boolean isDeleteBrokenBackups() {
        return deleteBrokenBackups;
    }

    public DateTimeFormatter getDateTimeFormatter() {
        return dateTimeFormatter;
    }

    public File getSizeCacheFile() {
        return sizeCacheFile;
    }

    public String getAlertBackupMessage() {
        return alertBackupMessage;
    }

    public String getAlertBackupRestartMessage() {
        return alertBackupRestartMessage;
    }
}