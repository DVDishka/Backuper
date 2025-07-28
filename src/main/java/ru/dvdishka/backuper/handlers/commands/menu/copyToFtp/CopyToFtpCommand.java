package ru.dvdishka.backuper.handlers.commands.menu.copyToFtp;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.FtpBackup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpSendFileFolderTask;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

import java.util.List;

public class CopyToFtpCommand extends Command {

    public CopyToFtpCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!Config.getInstance().getFtpConfig().isEnabled()) {
            cancelSound();
            returnFailure("FTP storage is disabled");
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

        for (FtpBackup ftpBackup : FtpBackup.getBackups()) {
            if (ftpBackup.getName().equals(localBackup.getName())) {
                cancelSound();
                returnFailure("Ftp storage already contains this backup");
                return;
            }
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("CopyToFtp", sender);

        Scheduler.getInstance().runAsync(Utils.plugin, () -> {

            String inProgressName = localBackup.getName() + " in progress";
            if (localBackup.getFileType().equals("(ZIP)")) {
                inProgressName += ".zip";
            }

            Task task = new FtpSendFileFolderTask(localBackup.getFile(),
                    FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), inProgressName),
                    false, true, true, List.of(Permissions.LOCAL_COPY_TO_FTP), sender);
            task.run();

            if (!task.isCancelled()) {
                FtpUtils.renameFile(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), inProgressName),
                        FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), localBackup.getFileName()),
                        sender);
            }

            sendMessage("CopyToFtp task completed");
        });
    }
}
