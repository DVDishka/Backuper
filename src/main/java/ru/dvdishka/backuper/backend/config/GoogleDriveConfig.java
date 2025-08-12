package ru.dvdishka.backuper.backend.config;

import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;

import java.io.File;
import java.util.HashMap;

public class GoogleDriveConfig {

    boolean enabled;
    boolean autoBackup;
    File tokenFolder;
    String backupsFolderId;
    boolean createBackuperFolder;
    int backupsNumber;
    long backupsWeight;
    boolean zipArchive;
    int zipCompressionLevel;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public File getTokenFolder() {
        return tokenFolder;
    }

    public String getRawBackupFolderId() {
        return backupsFolderId;
    }

    public String getBackupsFolderId() {
        if (!createBackuperFolder) {
            return backupsFolderId;
        }

        try {
            for (com.google.api.services.drive.model.File driveFile : GoogleDriveUtils.ls(backupsFolderId, "appProperties has { key='root' and value='true' }")) {
                if (driveFile.getName().equals("Backuper")) {
                    return driveFile.getId();
                }
            }
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to get Google Drive file list using dir id \"%s\"".formatted(backupsFolderId));
        }

        try {
            HashMap<String, String> properties = new HashMap<>();
            properties.put("root", "true");
            return GoogleDriveUtils.createFolder("Backuper", backupsFolderId, properties);

        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to create Backuper folder in Google Drive. Check if Google Drive account is linked");
            Backuper.getInstance().getLogManager().warn(e);
            return null;
        }
    }

    public int getBackupsNumber() {
        return backupsNumber;
    }

    public long getBackupsWeight() {
        return backupsWeight;
    }

    public boolean isZipArchive() {
        return zipArchive;
    }

    public int getZipCompressionLevel() {
        return zipCompressionLevel;
    }
}
