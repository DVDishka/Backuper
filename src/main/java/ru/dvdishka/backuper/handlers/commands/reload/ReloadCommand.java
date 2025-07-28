package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;

public class ReloadCommand extends Command {

    public ReloadCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backuper.isLocked()) {
            returnFailure("Blocked by another operation!");
            cancelSound();
            return;
        }

        buttonSound();

        Config.getInstance().setConfigField("lastBackup", Config.getInstance().getLastBackup());
        Config.getInstance().setConfigField("lastChange", Config.getInstance().getLastChange());

        Scheduler.getInstance().destroy(Utils.plugin);

        Initialization.initConfig(new File("plugins/Backuper/config.yml"), sender);
        Initialization.checkStorages(sender);
        Initialization.unifyBackupNameFormat(sender);

        Initialization.initAutoBackup();

        successSound();
    }
}
