package ru.dvdishka.backuper.handlers.commands.menu.unZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.tasks.zip.ZipToFolderTask;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

public class UnZIPCommand extends Command {

    public UnZIPCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        Backup backup = new Backup(backupName);

        if (backup.zipOrFolder().equals("(Folder)")) {
            cancelSound();
            returnFailure("Backup is already Folder!");
            return;
        }

        if (Backup.isLocked() || Backup.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("UnZIP", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            try {

                new ZipToFolderTask(backup.getZIPFile(), true, sender);

            } catch (Exception e) {

                Backup.unlock();

                Logger.getLogger().warn("The UnZIP task has been finished with an exception!", sender);
                Logger.getLogger().warn(this, e);

                cancelSound();
            }
        });
    }
}
