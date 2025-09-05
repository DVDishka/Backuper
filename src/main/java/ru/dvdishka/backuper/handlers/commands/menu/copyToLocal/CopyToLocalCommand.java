package ru.dvdishka.backuper.handlers.commands.menu.copyToLocal;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.FtpBackup;
import ru.dvdishka.backuper.backend.backup.GoogleDriveBackup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.backup.SftpBackup;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class CopyToLocalCommand extends Command {

    private String storage;

    public CopyToLocalCommand(String storage, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
        this.storage = storage;
    }

    @Override
    public void execute() {

        if (!ConfigManager.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled!");
            return;
        }

        if (storage.equals("sftp") && !ConfigManager.getInstance().getSftpConfig().isEnabled() ||
                storage.equals("ftp") && !ConfigManager.getInstance().getFtpConfig().isEnabled() ||
                storage.equals("googleDrive") && (!ConfigManager.getInstance().getGoogleDriveConfig().isEnabled() ||
                        !GoogleDriveUtils.checkConnection())) {
            cancelSound();
            if (!storage.equals("googleDrive")) {
                returnFailure("%s storage is disabled!".formatted(storage));
            } else {
                returnFailure("%s storage is disabled or Google account is not linked!".formatted(storage));
            }
            return;
        }

        ExternalBackup backup = null;
        Permissions permission = null;
        if (storage.equals("sftp")) {
            backup = SftpBackup.getInstance((String) arguments.get("backupName"));
            permission = Permissions.SFTP_COPY_TO_LOCAL;
        }
        if (storage.equals("ftp")) {
            backup = FtpBackup.getInstance((String) arguments.get("backupName"));
            permission = Permissions.FTP_COPY_TO_LOCAL;
        }
        if (storage.equals("googleDrive")) {
            backup = GoogleDriveBackup.getInstance((String) arguments.get("backupName"));
            permission = Permissions.GOOGLE_DRIVE_COPY_TO_LOCAL;
        }

        if (backup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
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

        final AsyncTask copyToLocalTask = backup.getCopyToLocalTask();
        Backuper.getInstance().getTaskManager().startTaskAsync(copyToLocalTask, sender, List.of(permission));
    }
}
