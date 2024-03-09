package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Scheduler;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;

public class ReloadCommand extends Command {

    public ReloadCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backup.isLocked()) {
            returnFailure("Unable to reload config while backup process is running!");
            return;
        }

        Config.getInstance().setConfigField("lastBackup", Config.getInstance().getLastBackup());
        Config.getInstance().setConfigField("lastChange", Config.getInstance().getLastChange());

        Scheduler.cancelTasks(Utils.plugin);

        Initialization.initConfig(new File("plugins/Backuper/config.yml"), sender);
        Initialization.initAutoBackup();
    }
}
