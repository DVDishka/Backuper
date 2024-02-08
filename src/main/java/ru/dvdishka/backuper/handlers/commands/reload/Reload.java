package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.back.common.Scheduler;
import ru.dvdishka.backuper.back.common.Backup;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.Initialization;
import ru.dvdishka.backuper.handlers.commands.common.CommandInterface;

import java.io.File;

public class Reload implements CommandInterface {

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        if (Backup.isBackupBusy) {
            returnFailure("Unable to reload plugin while backup process is running!", sender);
            return;
        }
        Initialization.initConfig(new File("plugins/Backuper/config.yml"), sender);
        Scheduler.cancelTasks(Common.plugin);
        Initialization.initAutoBackup();
    }
}
