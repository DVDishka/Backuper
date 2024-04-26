package ru.dvdishka.backuper.handlers.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.tasks.zip.DirToZipTask;
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

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        Backup backup = new Backup(backupName);

        if (backup.zipOrFolder().equals("(ZIP)")) {
            cancelSound();
            returnFailure("Backup is already ZIP!");
            return;
        }

        if (Backup.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        StatusCommand.sendTaskStartedMessage("ToZIP", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
            try {

                new DirToZipTask(backup.getFile(), true, sender).run();

            } catch (Exception e) {

                cancelSound();
                Backup.unlock();

                Logger.getLogger().warn("Something went wrong while running ToZIP task", sender);
                Logger.getLogger().warn(this, e);
            }
        });
    }
}
