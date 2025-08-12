package ru.dvdishka.backuper.backend.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.services.drive.model.File;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.exception.StorageQuotaExceededException;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.GoogleDriveDeleteDirTask;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GoogleDriveBackup extends ExternalBackup {

    private static final Cache<String, GoogleDriveBackup> backups = Caffeine
            .newBuilder()
            .build(GoogleDriveBackup::getInstance);
    private static final Cache<String, ArrayList<GoogleDriveBackup>> backupList = Caffeine
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .expireAfterAccess(5, TimeUnit.SECONDS)
            .build();

    private GoogleDriveBackup(String backupName) {
        this.backupName = backupName;
    }

    public static GoogleDriveBackup getInstance(String backupName) {

        backupName = backupName.replace(".zip", "");

        if (!checkBackupExistenceByName(backupName)) {
            return null;
        }
        return backups.get(backupName, GoogleDriveBackup::new);
    }

    public static ArrayList<GoogleDriveBackup> getBackups() {

        if (!Config.getInstance().getGoogleDriveConfig().isEnabled()) {
            return new ArrayList<>();
        }

        return backupList.get("all", (key) -> {

            ArrayList<GoogleDriveBackup> backups = new ArrayList<>();
            try {
                for (File driveFile : GoogleDriveUtils.ls(Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), null)) {
                    try {
                        GoogleDriveBackup backup = getInstance(driveFile.getName().replace(".zip", ""));

                        if (backup != null) {
                            backups.add(backup);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (StorageQuotaExceededException e) {
                throw new RuntimeException(e);
            }
            return backups;
        });
    }

    public static boolean checkBackupExistenceByName(String backupName) {

        if (!Config.getInstance().getGoogleDriveConfig().isEnabled()) {
            return false;
        }

        try {
            LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
        } catch (Exception e) {
            return false;
        }

        try {
            List<File> backupDriveFiles = GoogleDriveUtils.ls(Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), null);
            for (File backupDriveFile : backupDriveFiles) {
                if (backupDriveFile.getName().replace(".zip", "").equals(backupName)) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to connect to GoogleDrive or a Google account is not connected");
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public BaseAsyncTask getRawDeleteTask() {
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
