package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.back.common.Scheduler;
import ru.dvdishka.backuper.back.common.Backup;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.Initialization;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;

public class Reload extends Command {

    public Reload(CommandSender sender, CommandArguments arguments) {
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
