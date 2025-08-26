package ru.dvdishka.backuper.backend.backup;

import com.google.api.services.drive.model.File;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.GoogleDriveDeleteDirTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;

import java.time.LocalDateTime;
import java.util.Map;

public class GoogleDriveBackup extends ExternalBackup {

    private final String backupName;

    GoogleDriveBackup(String backupName) {
        this.backupName = backupName;
    }

    @Override
    public Task getRawDeleteTask() {
        return new GoogleDriveDeleteDirTask(getDriveFile().getId());
    }

    @Override
    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
    }

    @Override
    public String getName() {
        return backupName;
    }

    @Override
    long calculateByteSize() {

        try {
            File driveFile = GoogleDriveUtils.getService().files().get(getPath()).setFields("appProperties").execute();

            Map<String, String> appProperties = driveFile.getAppProperties();

            if (appProperties.get("size") != null) {
                return Long.parseLong(driveFile.getAppProperties().get("size"));
            }
        } catch (Exception ignored) {}

        try {
            long size = GoogleDriveUtils.getFileByteSize(getDriveFile().getId());
            saveSizeToFileProperties(size);
            return size;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Puts given size to file`s appProperties
     *
     * @param byteSize
     */
    public boolean saveSizeToFileProperties(long byteSize) {

        try {
            GoogleDriveUtils.addProperty(getDriveFile().getId(), "size", String.valueOf(byteSize));
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to save backup size to Google Drive");
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public BackupFileType getFileType() {
        try {
            return GoogleDriveUtils.getFileByName("%s.zip".formatted(backupName),
                    Config.getInstance().getGoogleDriveConfig().getBackupsFolderId()) != null ? BackupFileType.ZIP : BackupFileType.DIR;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to get Google Drive backup file type");
            Backuper.getInstance().getLogManager().warn(e);
            return null;
        }
    }

    @Override
    public String getFileName() {
        return getDriveFile().getName();
    }

    /**
     * @return Google Drive file ID
     */
    @Override
    public String getPath() {
        return getDriveFile().getId();
    }

    /**
     * Returns null if this backup doesn't exist
     *
     * @return
     */
    public File getDriveFile() {

        try {
            return GoogleDriveUtils.getFileByName(backupName + (BackupFileType.ZIP.equals(getFileType()) ? ".zip" : ""),
                    Config.getInstance().getGoogleDriveConfig().getBackupsFolderId()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
