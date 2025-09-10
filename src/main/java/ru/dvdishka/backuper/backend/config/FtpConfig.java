package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.Backuper;

import java.io.InputStreamReader;

@Getter
public class FtpConfig implements StorageConfig {

    private String id;

    private boolean enabled;
    private boolean autoBackup;
    private String backupsFolder;
    private String username;
    private String address;
    private String password;
    private String pathSeparatorSymbol;
    private int backupsNumber;
    private long backupsWeight;
    private int port;
    private boolean zipArchive;
    private int zipCompressionLevel;

    private ConfigurationSection config;

    public FtpConfig load(ConfigurationSection config) {
        this.config = config;
        this.id = config.getName();
        this.enabled = config.getBoolean("enabled", false);
        this.autoBackup = config.getBoolean("autoBackup", true);
        this.backupsFolder = config.getString("backupsFolder", "");
        this.pathSeparatorSymbol = config.getString("pathSeparatorSymbol", "/");
        this.backupsNumber = config.getInt("maxBackupsNumber", 0);
        this.backupsWeight = config.getLong("maxBackupsWeight", 0) * 1_048_576L;
        this.zipArchive = config.getBoolean("zipArchive", true);
        int zipCompressionLevel = config.getInt("zipCompressionLevel", 5);
        this.address = config.getString("auth.address", "");
        this.port = config.getInt("auth.port", 21);
        this.username = config.getString("auth.username", "");
        this.password = config.getString("auth.password", "");

        if (zipCompressionLevel > 9 || zipCompressionLevel < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            if (zipCompressionLevel < 0) {
                Backuper.getInstance().getLogManager().warn("%s.zipCompressionLevel must be >= 0, using 0 value...".formatted(config.getCurrentPath()));
                zipCompressionLevel = 0;
            }
            if (zipCompressionLevel > 9) {
                Backuper.getInstance().getLogManager().warn("ftp.zipCompressionLevel must be <= 9, using 9 value...".formatted(config.getCurrentPath()));
                zipCompressionLevel = 9;
            }
        }
        this.zipCompressionLevel = zipCompressionLevel;
        return this;
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("ftp_config.yml")));
    }
}
