package ru.dvdishka.backuper.backend.backup;

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
import java.util.HashMap;
import java.util.List;

public class GoogleDriveBackup extends Backup {

    private static HashMap<String, GoogleDriveBackup> backups = new HashMap<>();

    private GoogleDriveBackup(String backupName) {
        this.backupName = backupName;
    }

    public static GoogleDriveBackup getInstance(String backupName) {

        if (!checkBackupExistenceByName(backupName)) {
            return null;
        }
        if (backups.containsKey(backupName)) {
            return backups.get(backupName);
        }
        GoogleDriveBackup backup = new GoogleDriveBackup(backupName);
        backups.put(backupName, backup);
        return backup;
    }

    public static ArrayList<GoogleDriveBackup> getBackups() {

        if (!Config.getInstance().getGoogleDriveConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<GoogleDriveBackup> backups = new ArrayList<>();
        for (File driveFile : GoogleDriveUtils.ls(Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), null)) {
            try {
                GoogleDriveBackup backup = getInstance(driveFile.getName());

                if (backup != null) {
                    backups.add(backup);
                }
            } catch (Exception e) {
            }
        }
        return backups;
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
                if (backupDriveFile.getName().equals(backupName)) {
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
    public long getByteSize(CommandSender sender) {
        if (cachedBackupsSize.containsKey(backupName)) {
            return cachedBackupsSize.get(backupName);
        }

        long size = GoogleDriveUtils.getFileByteSize(getDriveFile(sender).getId(), sender);
        cachedBackupsSize.put(backupName, size);
        return size;
    }

    @Override
    public long getMbSize(CommandSender sender) {
        return getByteSize(sender) / 1024 / 1024;
    }

    /**
     * @return Possible values: "(Folder)" "(ZIP)"
     */
    @Override
    public String getFileType() {
        return "(Folder)";
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

    public File getDriveFile(CommandSender sender) {

        try {
            return GoogleDriveUtils.getFileByName(backupName,
                    Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(),
                    sender);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to connect to GoogleDrive or a Google account is not connected", sender);
            Logger.getLogger().warn(GoogleDriveBackup.class, e);
            return null;
        }
    }
}
