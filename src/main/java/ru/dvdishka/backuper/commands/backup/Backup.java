package ru.dvdishka.backuper.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.common.classes.CommandInterface;
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

        if (Common.isBackupRunning) {
            returnFailure("The backup process is already running!", sender);
            return;
        }

        Scheduler.getScheduler().runSync(Common.plugin, new BackupStarterTask(afterBackup, sender));

        returnSuccess("Backup process has been started!", sender);
    }
}