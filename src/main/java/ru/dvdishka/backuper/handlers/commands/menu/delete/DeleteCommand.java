package ru.dvdishka.backuper.handlers.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

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
                        !GoogleDriveUtils.isAuthorized(sender))) {
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

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        Backup backup = null;

        if (storage.equals("local")) {
            backup = LocalBackup.getInstance(backupName);
        }
        if (storage.equals("sftp")) {
            backup = SftpBackup.getInstance(backupName);
        }
        if (storage.equals("ftp")) {
            backup = FtpBackup.getInstance(backupName);
        }
        if (storage.equals("googleDrive")) {
            backup = GoogleDriveBackup.getInstance(backupName);
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("Delete", sender);

        final Backup finalBackup = backup;

        Scheduler.getInstance().runAsync(Utils.plugin, () -> {

            try {
                finalBackup.delete(true, sender);
                successSound();
                sendMessage("Delete task completed");

            } catch (Exception e) {

                Logger.getLogger().warn("Delete task has been finished with an exception!", sender);
                Logger.getLogger().warn(this.getClass(), e);
                cancelSound();
            }
        });
    }
}
