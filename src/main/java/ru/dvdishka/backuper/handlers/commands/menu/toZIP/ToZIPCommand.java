package ru.dvdishka.backuper.handlers.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.tasks.local.zip.tozip.ConvertFolderToZipTask;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

public class ToZIPCommand extends Command {

    public ToZIPCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!LocalBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        LocalBackup localBackup = LocalBackup.getInstance(backupName);

        if (localBackup.zipOrFolder().equals("(ZIP)")) {
            cancelSound();
            returnFailure("Backup is already ZIP!");
            return;
        }

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("ToZIP", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
            try {

                new ConvertFolderToZipTask(localBackup.getFile(), true, sender).run();

                sendMessage("ToZIP task completed");

            } catch (Exception e) {

                cancelSound();
                Backuper.unlock();

                Logger.getLogger().warn("Something went wrong while running ToZIP task", sender);
                Logger.getLogger().warn(this, e);
            }
        });
    }
}
