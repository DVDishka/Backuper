package ru.dvdishka.backuper.handlers.commands.menu.unZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

public class UnZIPCommand extends Command {

    public UnZIPCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("local storage is disabled!");
            return;
        }

        if (!LocalBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        LocalBackup localBackup = LocalBackup.getInstance(backupName);

        if (localBackup.getFileType().equals("(Folder)")) {
            cancelSound();
            returnFailure("Backup is already Folder!");
            return;
        }

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("UnZIP", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            try {
                localBackup.unZip(true, sender);
                sendMessage("UnZIP task completed");

            } catch (Exception e) {

                Backuper.unlock();

                Logger.getLogger().warn("The UnZIP task has been finished with an exception!", sender);
                Logger.getLogger().warn(this.getClass(), e);

                cancelSound();
            }
        });
    }
}
