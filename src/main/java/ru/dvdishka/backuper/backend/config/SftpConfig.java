package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.Backuper;

import java.io.InputStreamReader;

@Getter
public class SftpConfig implements PathStorageConfig {

    private String id;

    private boolean enabled;
    private boolean autoBackup;
    private String sshConfigFilePath;
    private String backupsFolder;
    private String authType;
    private String username;
    private String address;
    private String password;
    private String knownHostsFilePath;
    private String useKnownHostsFile;
    private String keyFilePath;
    private String pathSeparatorSymbol;
    private int backupsNumber;
    private long backupsWeight;
    private boolean zipArchive;
    private int zipCompressionLevel;
    private int port;

    private ConfigurationSection config;

    public SftpConfig load(ConfigurationSection config) {
        this.config = config;
        this.id = config.getName();
        this.enabled = config.getBoolean("enabled");
        this.autoBackup = config.getBoolean("autoBackup");
        this.backupsFolder = config.getString("backupsFolder");
        this.pathSeparatorSymbol = config.getString("pathSeparatorSymbol");
        int backupsNumber = config.getInt("maxBackupsNumber");
        long backupsWeight = config.getLong("maxBackupsWeight") * 1_048_576L;
        this.zipArchive = config.getBoolean("zipArchive");
        int zipCompressionLevel = config.getInt("zipCompressionLevel");
        this.keyFilePath = config.getString("auth.keyFilePath");
        this.authType = config.getString("auth.authType");
        this.username = config.getString("auth.username");
        this.password = config.getString("auth.password");
        this.address = config.getString("auth.address");
        this.port = config.getInt("auth.port");
        this.useKnownHostsFile = config.getString("auth.useKnownHostsFile");
        this.knownHostsFilePath = config.getString("auth.knownHostsFilePath");
        this.sshConfigFilePath = config.getString("auth.sshConfigFilePath");

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
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("sftp_config.yml")));
    }
}
