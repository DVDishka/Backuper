package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.common.CommandInterface;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.classes.Scheduler;
import ru.dvdishka.backuper.tasks.BackupStarterTask;

public class Backup implements CommandInterface {

    private String afterBackup = "NOTHING";

    public Backup() {}

    public Backup(String afterBackup) {

        this.afterBackup = afterBackup;
    }

    public void execute(CommandSender sender, CommandArguments args) {

        Scheduler.getScheduler().runSync(Common.plugin, new BackupStarterTask(afterBackup));

        returnSuccess("Backup process has been started!\nYou can see the result in the console", sender);
    }
}
