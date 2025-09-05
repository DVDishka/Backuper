package ru.dvdishka.backuper.handlers.commands.menu.copyToGoogleDrive;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.GoogleDriveBackup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.backend.task.GoogleDriveSendDirTask;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class CopyToGoogleDriveCommand extends Command {

    public CopyToGoogleDriveCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!ConfigManager.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!ConfigManager.getInstance().getGoogleDriveConfig().isEnabled() || !GoogleDriveUtils.checkConnection()) {
            cancelSound();
            returnFailure("Google Drive storage is disabled or Google account is not linked!");
            return;
        }

        LocalBackup localBackup = LocalBackup.getInstance((String) arguments.get("backupName"));

        if (localBackup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        for (GoogleDriveBackup googleDriveBackup : GoogleDriveBackup.getBackups()) {
            if (googleDriveBackup.getName().equals(localBackup.getName())) {
                cancelSound();
                returnFailure("Google Drive storage already contains this backup");
                return;
            }
        }

        buttonSound();

        Backuper.getInstance().getScheduleManager().runAsync(() -> {

            String inProgressName = "%s in progress".formatted(localBackup.getName());
            if (Backup.BackupFileType.ZIP.equals(localBackup.getFileType())) {
                inProgressName = "%s.zip".formatted(inProgressName);
            }

            AsyncTask task = new GoogleDriveSendDirTask(localBackup.getFile(),
                    ConfigManager.getInstance().getGoogleDriveConfig().getBackupsFolderId(),
                    inProgressName,
                    true, true
            );
            Backuper.getInstance().getTaskManager().startTask(task, sender, List.of(Permissions.LOCAL_COPY_TO_GOOGLE_DRIVE));

            if (!task.isCancelled()) {
                try {
                    GoogleDriveUtils.renameFile(
                            GoogleDriveUtils.getFileByName(inProgressName, ConfigManager.getInstance().getGoogleDriveConfig().getBackupsFolderId()).getId(),
                            localBackup.getFileName()
                    );
                    Backup.saveBackupSizeToCache(Backup.StorageType.GOOGLE_DRIVE, localBackup.getName(), task.getTaskMaxProgress());
                } catch (Exception e) {
                    Backuper.getInstance().getLogManager().warn("Failed to rename backup \"in-progress\" file on Google Drive");
                }
            }
        });
    }
}
