package ru.dvdishka.backuper.handlers.commands.menu.copyToLocal;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.classes.SftpBackup;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpGetFileFolderTask;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

import java.io.File;

public class CopyToLocalCommand extends Command {

    public CopyToLocalCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        SftpBackup sftpBackup = SftpBackup.getInstance((String) arguments.get("backupName"));

        if (sftpBackup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        for (LocalBackup localBackup : LocalBackup.getBackups()) {
            if (localBackup.getName().equals(sftpBackup.getName())) {
                cancelSound();
                returnFailure("Local storage already contains this backup");
                return;
            }
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("CopyToLocal", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            new SftpGetFileFolderTask(sftpBackup.getPath(), new File(Config.getInstance().getLocalConfig().getBackupsFolder()),
                    true, true, sender).run();

            sendMessage("CopyToLocal task completed");
        });
    }
}
