package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.Scheduler;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.backend.utils.Common;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;

public class ReloadCommand extends Command {

    public ReloadCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backup.isBackupBusy) {
            returnFailure("Unable to reload plugin while backup process is running!");
            return;
        }
        Initialization.initConfig(new File("plugins/Backuper/config.yml"), sender);
        Scheduler.cancelTasks(Common.plugin);
        Initialization.initAutoBackup();
    }
}
