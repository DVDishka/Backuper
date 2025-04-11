package ru.dvdishka.backuper.backend.config;

import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;

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

        for (com.google.api.services.drive.model.File driveFile : GoogleDriveUtils.ls(backupsFolderId, "appProperties has { key='root' and value='true' }", null)) {
            if (driveFile.getName().equals("Backuper")) {
                return driveFile.getId();
            }
        }

        try {
            HashMap<String, String> properties = new HashMap<>();
            properties.put("root", "true");
            return GoogleDriveUtils.createFolder("Backuper", backupsFolderId, properties, null);

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to create Backuper folder in Google Drive. Check if Google Drive account is linked");
            Logger.getLogger().warn(this.getClass(), e);
            return null;
        }
    }

    public int getBackupsNumber() {
        return backupsNumber;
    }

    public long getBackupsWeight() {
        return backupsWeight;
    }
}
