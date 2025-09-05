package ru.dvdishka.backuper.backend.config;

import dev.jorel.commandapi.CommandAPI;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.*;

import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class ConfigManager {

    private File configFile;

    @Getter
    private long lastBackup;
    @Getter
    private long lastChange;

    @Getter
    private BackupConfig backupConfig;
    @Getter
    private ServerConfig serverConfig;

    public synchronized void setConfigField(String path, Object value) {

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        config.set(path, value);
        try {
            config.save(configFile);
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to save config.yml file");
        }
    }

    public void updateLastChange() {
        this.lastChange = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

    public void updateLastBackup() {
        this.lastBackup = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

    public void load(File configFile, CommandSender sender) {

        Backuper.getInstance().getLogManager().devLog("loading config...", sender);

        this.configFile = configFile;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        ConfigBackwardsCompatibility.configBelow4(config);
        ConfigBackwardsCompatibility.configBelow8(config);
        ConfigBackwardsCompatibility.configBelow13(config);

        loadBackupConfig(config);
        loadStorages(config);
        loadServerConfig(config);

        this.lastBackup = config.getLong("lastBackup", 0);
        this.lastChange = config.getLong("lastChange", 0);

        config.set("configVersion", YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("config.yml"))).getString("configVersion"));
        try {
            config.save(configFile);
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to save repaired config.yml file", sender);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            CommandAPI.updateRequirements(player);
        }

        Backuper.getInstance().getLogManager().devLog("Config has been loaded", sender);
    }

    private void loadBackupConfig(ConfigurationSection config) {
        ConfigurationSection backupSection = config.getConfigurationSection("backup");
        this.backupConfig = (BackupConfig) new BackupConfig().repairThenLoad(backupSection);
    }

    private void loadStorages(ConfigurationSection config) {
        ConfigurationSection storagesSection = config.getConfigurationSection("storages");
        storagesSection.getKeys(false).forEach(key -> {
            String storageId = key;
            ConfigurationSection storageSection = storagesSection.getConfigurationSection(storageId);

            String storageType = storageSection.getString("type", "");
            Storage storage = switch (storageType) {
                case "local" -> new LocalStorage((LocalConfig) new LocalConfig().repairThenLoad(storageSection));
                case "ftp" -> new FtpStorage((FtpConfig) new FtpConfig().repairThenLoad(storageSection));
                case "sftp" -> new SftpStorage((SftpConfig) new SftpConfig().repairThenLoad(storageSection));
                case "googleDrive" -> new GoogleDriveStorage((GoogleDriveConfig) new GoogleDriveConfig().repairThenLoad(storageSection));
                default -> {
                    Backuper.getInstance().getLogManager().warn("Wrong storage type \"%s\" in \"%s\" storage in config.yml. Skipping this storage...".formatted(storageType, storageId));
                    yield null;
                }
            };
            Backuper.getInstance().getStorageManager().registerStorage(storageId, storage);
        });
    }

    private void loadServerConfig(ConfigurationSection config) {
        ConfigurationSection serverSection = config.getConfigurationSection("server");
        this.serverConfig = (ServerConfig) new ServerConfig().repairThenLoad(serverSection);
    }
}