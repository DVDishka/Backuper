package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.Backuper;

import java.io.File;
import java.io.InputStreamReader;

@Getter
public class ServerConfig implements Config {

    private long alertTimeBeforeRestart;
    private boolean betterLogging;
    private boolean alertOnlyServerRestart;
    private boolean checkUpdates;
    private File sizeCacheFile;
    private String alertBackupMessage;
    private String alertBackupRestartMessage;
    private int threadsNumber;

    private ConfigurationSection config;

    @Override
    public Config load(ConfigurationSection config) {
        this.config = config;
        this.alertBackupMessage = config.getString("alertBackupMessage");
        this.alertBackupRestartMessage = config.getString("alertBackupRestartMessage");
        this.sizeCacheFile = new File(config.getString("sizeCacheFile"));
        this.threadsNumber = config.getInt("threadsNumber");
        this.betterLogging = config.getBoolean("betterLogging");
        this.alertTimeBeforeRestart = config.getLong("alertTimeBeforeRestart");
        this.alertOnlyServerRestart = config.getBoolean("alertOnlyServerRestart");
        this.checkUpdates = config.getBoolean("checkUpdates");
        return this;
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("server_config.yml")));
    }
}
