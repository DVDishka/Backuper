package ru.dvdishka.backuper.handlers.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class DeleteCommand extends Command {

    private String storage = "";

    public DeleteCommand(String storage, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);

        this.storage = storage;
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (storage.equals("local") && !Config.getInstance().getLocalConfig().isEnabled() ||
                storage.equals("sftp") && !Config.getInstance().getSftpConfig().isEnabled() ||
                storage.equals("ftp") && !Config.getInstance().getFtpConfig().isEnabled() ||
                storage.equals("googleDrive") && (!Config.getInstance().getGoogleDriveConfig().isEnabled() ||
                        !GoogleDriveUtils.checkConnection())) {
            cancelSound();
            if (!storage.equals("googleDrive")) {
                returnFailure(storage + " storage is disabled!");
            } else {
                returnFailure(storage + " storage is disabled or Google account is not linked!");
            }
            return;
        }

        if (storage.equals("local") && !LocalBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("sftp") && !SftpBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("ftp") && !FtpBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("googleDrive") && !GoogleDriveBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        Backup backup = null;
        Permissions permission = null;
        if (storage.equals("local")) {
            backup = LocalBackup.getInstance(backupName);
            permission = Permissions.LOCAL_DELETE;
        }
        if (storage.equals("sftp")) {
            backup = SftpBackup.getInstance(backupName);
            permission = Permissions.SFTP_DELETE;
        }
        if (storage.equals("ftp")) {
            backup = FtpBackup.getInstance(backupName);
            permission = Permissions.FTP_DELETE;
        }
        if (storage.equals("googleDrive")) {
            backup = GoogleDriveBackup.getInstance(backupName);
            permission = Permissions.GOOGLE_DRIVE_DELETE;
        }

        buttonSound();

        final Backup finalBackup = backup;

        BaseAsyncTask task = finalBackup.getDeleteTask();
        Backuper.getInstance().getTaskManager().startTaskAsync(task, sender, List.of(permission));
    }
}
