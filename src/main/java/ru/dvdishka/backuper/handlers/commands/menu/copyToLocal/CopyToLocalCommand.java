package ru.dvdishka.backuper.handlers.commands.menu.copyToLocal;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

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

        ExternalBackup backup = null;

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

        final Task copyToLocalTask = backup.getCopyToLocalTask(true, sender);

        Scheduler.getInstance().runAsync(Utils.plugin, () -> {
            copyToLocalTask.run();

            sendMessage("CopyToLocal task completed");
        });
    }
}
