package ru.dvdishka.backuper.handlers.commands.menu.copyToGoogleDrive;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.GoogleDriveBackup;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.googleDrive.GoogleDriveSendFileFolderTask;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

import java.util.List;

public class CopyToGoogleDriveCommand extends Command {

    public CopyToGoogleDriveCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!Config.getInstance().getGoogleDriveConfig().isEnabled() || !GoogleDriveUtils.isAuthorized(sender)) {
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

        if (Backuper.isLocked()) {
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

        StatusCommand.sendTaskStartedMessage("CopyToGoogleDrive", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            String inProgressName = localBackup.getName() + " in progress";
            if (localBackup.getFileType().equals("(ZIP)")) {
                inProgressName += ".zip";
            }

            Task task = new GoogleDriveSendFileFolderTask(localBackup.getFile(),
                    Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(),
                    inProgressName,
                    true, true, true,
                    List.of(Permissions.LOCAL_COPY_TO_GOOGLE_DRIVE),
                    sender);
            task.run();

            if (!task.isCancelled()) {
                GoogleDriveUtils.renameFile(
                        GoogleDriveUtils.getFileByName(inProgressName, Config.getInstance().getGoogleDriveConfig().getBackupsFolderId(), sender).getId(),
                        localBackup.getFileName(),
                        sender);
            }

            sendMessage("CopyToGoogleDrive task completed");
        });
    }
}
