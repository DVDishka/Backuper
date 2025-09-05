package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.GoogleDriveStorage;

import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;

@Getter
public class GoogleDriveConfig implements StorageConfig {

    private String id;

    private boolean enabled;
    private boolean autoBackup;
    private File tokenFolder;
    private String backupsFolderId;
    private boolean createBackuperFolder;
    private int backupsNumber;
    private long backupsWeight;
    private boolean zipArchive;
    private int zipCompressionLevel;

    public GoogleDriveConfig load(ConfigurationSection config) {
        this.id = config.getName();
        this.enabled = config.getBoolean("googleDrive.enabled", false);
        this.autoBackup = config.getBoolean("googleDrive.autoBackup", true);
        this.backupsFolderId = config.getString("googleDrive.backupsFolderId", "");
        String googleDriveTokenFolder = config.getString("googleDrive.auth.tokenFolderPath", "plugins/Backuper/GoogleDrive/tokens");
        this.tokenFolder = new File(googleDriveTokenFolder);
        this.createBackuperFolder = config.getBoolean("googleDrive.createBackuperFolder", true);
        this.backupsNumber = config.getInt("googleDrive.maxBackupsNumber", 0);
        this.backupsWeight = config.getLong("googleDrive.maxBackupsWeight", 0) * 1_048_576L;
        this.zipArchive = config.getBoolean("googleDrive.zipArchive", true);
        int zipCompressionLevel = config.getInt("googleDrive.zipCompressionLevel", 5);

        if (zipCompressionLevel > 9 || zipCompressionLevel < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            if (zipCompressionLevel < 0) {
                Backuper.getInstance().getLogManager().warn("googleDrive.zipCompressionLevel must be >= 0, using 0 value...");
                zipCompressionLevel = 0;
            }
            if (zipCompressionLevel > 9) {
                Backuper.getInstance().getLogManager().warn("googleDrive.zipCompressionLevel must be <= 9, using 9 value...");
                zipCompressionLevel = 9;
            }
        }
        this.zipCompressionLevel = zipCompressionLevel;
        return this;
    }

    public String getRawBackupFolderId() {
        return backupsFolderId;
    }

    public String getBackupsFolderId() {
        if (!createBackuperFolder) {
            return backupsFolderId;
        }

        GoogleDriveStorage storage = (GoogleDriveStorage) Backuper.getInstance().getStorageManager().getStorage(id);
        if (storage == null) {
            throw new RuntimeException("Tried to get backupsFolder from unregistered \"%s\" GoogleDrive storage".formatted(id));
        }

        for (com.google.api.services.drive.model.File driveFile : storage.ls(backupsFolderId, "appProperties has { key='root' and value='true' }")) {
            if (driveFile.getName().equals("Backuper")) {
                return driveFile.getId();
            }
        }
        HashMap<String, String> properties = new HashMap<>();
        properties.put("root", "true");
        storage.createDir("Backuper", backupsFolderId, properties);
        return storage.getFileByName("Backuper", backupsFolderId).getId();
    }

    @Override
    public String getBackupsFolder() {
        return getBackupsFolderId();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("google_drive_config.yml")));
    }
}
