package ru.dvdishka.backuper.backend.config;

import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;

import java.io.File;

public class GoogleDriveConfig {

    boolean enabled;
    boolean autoBackup;
    File tokensFolder;
    String backupsFolderId;
    boolean createBackuperFolder;
    boolean moveFilesToTrash;

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public File getTokensFolder() {
        return tokensFolder;
    }

    public String getBackupsFolderId() {
        if (!createBackuperFolder) {
            return backupsFolderId;
        }

        for (com.google.api.services.drive.model.File driveFile : GoogleDriveUtils.ls(backupsFolderId, null)) {
            if (driveFile.getName().equals("Backuper")) {
                return driveFile.getId();
            }
        }

        try {
            return GoogleDriveUtils.createFolder("Backuper", backupsFolderId, null);

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to create Backuper folder in Google Drive. Check if Google Drive account is linked");
            Logger.getLogger().warn(this.getClass(), e);
            return null;
        }
    }

    public boolean isMoveFilesToTrash() {
        return moveFilesToTrash;
    }
}
