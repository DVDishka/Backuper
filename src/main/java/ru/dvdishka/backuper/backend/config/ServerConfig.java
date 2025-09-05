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

    @Override
    public Config load(ConfigurationSection config) {
        this.alertBackupMessage = config.getString("alertBackupMessage", "Server will be backed up in %d second(s)");
        this.alertBackupRestartMessage = config.getString("alertBackupRestartMessage", "Server will be backed up and restarted in %d second(s)");
        this.sizeCacheFile = new File(config.getString("sizeCacheFile", "./plugins/Backuper/sizeCache.json"));
        this.betterLogging = config.getBoolean("betterLogging", false);
        this.alertTimeBeforeRestart = config.getLong("alertTimeBeforeRestart", 60);
        this.alertOnlyServerRestart = config.getBoolean("alertOnlyServerRestart", true);
        this.checkUpdates = config.getBoolean("checkUpdates", true);
        return this;
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("server_config.yml")));
    }
}
