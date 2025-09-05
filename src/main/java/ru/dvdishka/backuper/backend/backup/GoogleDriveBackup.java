package ru.dvdishka.backuper.backend.backup;

import com.google.api.services.drive.model.File;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.GoogleDriveStorage;

public class GoogleDriveBackup implements Backup {

    private final String backupName;
    private final GoogleDriveStorage storage;

    GoogleDriveBackup(GoogleDriveStorage storage, String backupName) {
        this.backupName = backupName;
        this.storage = storage;
    }

    @Override
    public GoogleDriveStorage getStorage() {
        return storage;
    }

    @Override
    public String getName() {
        return backupName;
    }

    /**
     * Puts given size to file`s appProperties
     */
    public boolean saveSizeToFileProperties(long byteSize) {

        try {
            storage.addProperty(getDriveFile().getId(), "size", String.valueOf(byteSize));
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to save backup size to Google Drive");
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    /**
     * @return Returns null if this backup doesn't exist
     */
    public File getDriveFile() {

        try {
            return storage.getFileByName(backupName + (BackupFileType.ZIP.equals(getFileType()) ? ".zip" : ""),
                    storage.getConfig().getBackupsFolderId());
        } catch (Exception e) {
            return null;
        }
    }
}
