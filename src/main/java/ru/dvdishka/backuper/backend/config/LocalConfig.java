package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.InputStreamReader;

@Getter
public class LocalConfig implements PathStorageConfig {

    private String id;

    private boolean enabled;
    private boolean autoBackup;
    private String backupsFolder;
    private int backupsNumber;
    private long backupsWeight;
    private boolean zipArchive;
    private int zipCompressionLevel;
    private String pathSeparatorSymbol = Utils.isWindows ? "\\" : "/";

    private ConfigurationSection config;

    public LocalConfig load(ConfigurationSection config, String name) {
        this.config = config;
        this.id = name;
        this.enabled = config.getBoolean("enabled");
        this.autoBackup = config.getBoolean("autoBackup");
        int backupsNumber = config.getInt("maxBackupsNumber");
        long backupsWeight = config.getLong("maxBackupsWeight") * 1_048_576L;
        this.zipArchive = config.getBoolean("zipArchive");
        this.backupsFolder = config.getString("backupsFolder");
        int zipCompressionLevel = config.getInt("zipCompressionLevel");

        if (backupsNumber < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.maxBackupsNumber must be >= 0, using default 0 value...".formatted(config.getCurrentPath()));
            backupsNumber = 0;
        }
        this.backupsNumber = backupsNumber;

        if (backupsWeight < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.maxBackupsWeight must be >= 0, using default 0 value...".formatted(config.getCurrentPath()));
            backupsWeight = 0;
        }
        this.backupsWeight = backupsWeight;

        if (zipCompressionLevel > 9 || zipCompressionLevel < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            if (zipCompressionLevel < 0) {
                Backuper.getInstance().getLogManager().warn("%s.zipCompressionLevel must be >= 0, using 0 value...".formatted(config.getCurrentPath()));
                zipCompressionLevel = 0;
            }
            if (zipCompressionLevel > 9) {
                Backuper.getInstance().getLogManager().warn("%s.zipCompressionLevel must be <= 9, using 9 value...".formatted(config.getCurrentPath()));
                zipCompressionLevel = 9;
            }
        }
        this.zipCompressionLevel = zipCompressionLevel;
        return this;
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("local_config.yml")));
    }
}
