package ru.dvdishka.backuper.handlers.commands.menu.copyToSftp;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.classes.SftpBackup;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpSendFileFolderTask;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

public class CopyToSftpCommand extends Command {

    public CopyToSftpCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            cancelSound();
            returnFailure("Sftp storage is disabled");
            return;
        }

        LocalBackup localBackup = LocalBackup.getInstance((String) arguments.get("backupName"));

        if (localBackup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        for (SftpBackup sftpBackup : SftpBackup.getBackups()) {
            if (sftpBackup.getName().equals(localBackup.getName())) {
                cancelSound();
                returnFailure("Sftp storage already contains this backup");
                return;
            }
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("CopyToSftp", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            new SftpSendFileFolderTask(localBackup.getFile(), Config.getInstance().getSftpConfig().getBackupsFolder(),
                    true, true, true, sender).run();

            sendMessage("CopyToSftp task completed");
        });
    }
}
