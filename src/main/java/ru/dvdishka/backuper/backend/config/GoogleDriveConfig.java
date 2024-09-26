package ru.dvdishka.backuper.backend.config;

import java.io.File;

public class GoogleDriveConfig {

    boolean enabled;
    boolean autoBackup;
    File credentialsFile;
    File tokensFolder;
    String backupsFolderId;

    public File getCredentialsFile() {
        return credentialsFile;
    }

    public File getTokensFolder() {
        return tokensFolder;
    }

    public String getBackupsFolderId() {
        return backupsFolderId;
    }
}
