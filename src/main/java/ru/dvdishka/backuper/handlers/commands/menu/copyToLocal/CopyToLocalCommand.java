package ru.dvdishka.backuper.handlers.commands.menu.copyToLocal;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpGetFileFolderTask;
import ru.dvdishka.backuper.backend.tasks.googleDrive.GoogleDriveGetFileFolderTask;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpGetFileFolderTask;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

import java.io.File;
import java.util.List;

public class CopyToLocalCommand extends Command {

    private String storage;

    public CopyToLocalCommand(String storage, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
        this.storage = storage;
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled!");
            return;
        }

        if (storage.equals("sftp") && !Config.getInstance().getSftpConfig().isEnabled() ||
                storage.equals("ftp") && !Config.getInstance().getFtpConfig().isEnabled() ||
                storage.equals("googleDrive") && (!Config.getInstance().getGoogleDriveConfig().isEnabled() ||
                        !GoogleDriveUtils.isAuthorized(sender))) {
            cancelSound();
            if (!storage.equals("googleDrive")) {
                returnFailure(storage + " storage is disabled!");
            } else {
                returnFailure(storage + " storage is disabled or Google account is not linked!");
            }
            return;
        }

        Backup backup = null;

        if (storage.equals("sftp")) {
            backup = SftpBackup.getInstance((String) arguments.get("backupName"));
        }
        if (storage.equals("ftp")) {
            backup = FtpBackup.getInstance((String) arguments.get("backupName"));
        }
        if (storage.equals("googleDrive")) {
            backup = GoogleDriveBackup.getInstance((String) arguments.get("backupName"));
        }

        if (backup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        for (LocalBackup localBackup : LocalBackup.getBackups()) {
            if (localBackup.getName().equals(backup.getName())) {
                cancelSound();
                returnFailure("Local storage already contains this backup");
                return;
            }
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("CopyToLocal", sender);

        final Task copyToLocalTask;

        String inProgressName = backup.getName() + " in progress";
        if (backup.getFileType().equals("(ZIP)")) {
            inProgressName += ".zip";
        }
        File inProgressFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder(), inProgressName);

        copyToLocalTask = switch (storage) {
            case "sftp" -> new SftpGetFileFolderTask(backup.getPath(), inProgressFile,
                    false, true, List.of(Permissions.SFTP_COPY_TO_LOCAL), sender);

            case "ftp" -> new FtpGetFileFolderTask(backup.getPath(), inProgressFile,
                    false, true, List.of(Permissions.FTP_COPY_TO_LOCAL), sender);

            case "googleDrive" -> new GoogleDriveGetFileFolderTask(backup.getPath(), inProgressFile,
                    false, true, List.of(Permissions.GOOGLE_DRIVE_COPY_TO_LOCAL), sender);

            default -> null;
        };

        final String backupFileName = backup.getFileName();

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
            copyToLocalTask.run();

            if (!copyToLocalTask.isCancelled()) {
                if (!inProgressFile.renameTo(new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName))) {
                    Logger.getLogger().warn("Failed to rename local file: \"" + inProgressFile.getAbsolutePath() + "\" to \"" +
                            new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName).getAbsolutePath() + "\"", sender);
                }
            }

            sendMessage("CopyToLocal task completed");
        });
    }
}
