package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;

public class ReloadCommand extends Command {

    public ReloadCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("Blocked by another operation!");
            cancelSound();
            return;
        }

        buttonSound();

        ConfigManager.getInstance().setConfigField("lastBackup", ConfigManager.getInstance().getLastBackup());
        ConfigManager.getInstance().setConfigField("lastChange", ConfigManager.getInstance().getLastChange());

        Backuper.getInstance().getScheduleManager().destroy(Backuper.getInstance());

        Initialization.initConfig(new File("plugins/Backuper/config.yml"), sender);
        Initialization.checkStorages(sender);
        Initialization.unifyBackupNameFormat(sender);

        Initialization.initAutoBackup();

        successSound();
    }
}
