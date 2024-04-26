package ru.dvdishka.backuper.handlers.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.tasks.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

import java.io.File;

public class DeleteCommand extends Command {

    public DeleteCommand(CommandSender sender, CommandArguments arguments) {
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

        Backup backup = new Backup(backupName);

        if (Backup.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        File backupFile = backup.getFile();

        StatusCommand.sendTaskStartedMessage("Delete", sender);

        Logger.getLogger().log("The Delete Backup process has been started, it may take some time...", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            try {

                new DeleteDirTask(backupFile, true, sender).run();

                Logger.getLogger().log("The DeleteDir process has been finished", sender);
                successSound();

            } catch (Exception e) {

                Logger.getLogger().warn("The Delete Backup process has been finished with an exception!", sender);
                cancelSound();
            }
        });
    }
}
