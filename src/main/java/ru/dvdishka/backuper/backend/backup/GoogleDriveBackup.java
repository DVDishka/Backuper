package ru.dvdishka.backuper.backend.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.api.services.drive.model.File;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.googleDrive.GoogleDriveDeleteFileFolderTask;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            for (File driveFile : GoogleDriveUtils.ls(Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), null)) {
                try {
                    GoogleDriveBackup backup = getInstance(driveFile.getName().replace(".zip", ""));

                    if (backup != null) {
                        backups.add(backup);
                    }
                } catch (Exception ignored) {}
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
            Logger.getLogger().warn("Failed to connect to GoogleDrive or a Google account is not connected");
            Logger.getLogger().warn(GoogleDriveBackup.class, e);
            return false;
        }
    }

    @Override
    Task getDirectDeleteTask(boolean setLocked, CommandSender sender) {
        return new GoogleDriveDeleteFileFolderTask(getDriveFile(sender).getId(), setLocked, List.of(Permissions.GOOGLE_DRIVE_DELETE), sender);
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
    long calculateByteSize(CommandSender sender) {

        try {
            File driveFile = GoogleDriveUtils.getService(sender).files().get(getPath()).setFields("appProperties").execute();

            Map<String, String> appProperties = driveFile.getAppProperties();

            if (appProperties.get("size") != null) {
                return Long.parseLong(driveFile.getAppProperties().get("size"));
            }
        } catch (Exception ignored) {}

        try {
            long size = GoogleDriveUtils.getFileByteSize(getDriveFile(sender).getId(), sender);
            saveSizeToFileProperties(size, sender);
            return size;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Puts given size to file`s appProperties
     * @param byteSize
     * @param sender
     */
    public void saveSizeToFileProperties(long byteSize, CommandSender sender) {

        try {

            GoogleDriveUtils.addProperty(getDriveFile(sender).getId(), "size", String.valueOf(byteSize), sender);

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to save backup size to Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveBackup.class, e);
        }
    }

    /**
     * @return Possible values: "(Folder)" "(ZIP)"
     */
    @Override
    public String getFileType() {
        return GoogleDriveUtils.getFileByName(backupName + ".zip",
                Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), null) != null ? "(ZIP)" : "(Folder)";
    }

    @Override
    public String getFileName() {
        return getDriveFile(null).getName();
    }

    /**
     * @return Google Drive file ID
     */
    @Override
    public String getPath() {
        return getDriveFile(null).getId();
    }

    /**
     * Returns null if there is this backup does not exist
     * @param sender
     * @return
     */
    public File getDriveFile(CommandSender sender) {

        try {
            return GoogleDriveUtils.getFileByName(backupName + (Objects.equals(getFileType(), "(ZIP)") ? ".zip" : ""),
                    Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(),
                    sender);
        } catch (Exception e) {
            return null;
        }
    }
}
