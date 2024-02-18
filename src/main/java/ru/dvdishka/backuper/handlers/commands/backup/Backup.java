package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.common.Scheduler;

public class Backup extends Command {

    private String afterBackup = "NOTHING";

    public Backup(CommandSender sender, CommandArguments args, String afterBackup) {

        super(sender, args);
        this.afterBackup = afterBackup;
    }

    public Backup(CommandSender sender, CommandArguments args) {

        super(sender, args);
    }

    public void execute() {

        if (ru.dvdishka.backuper.back.common.Backup.isBackupBusy) {
            returnFailure("Blocked by another operation!");
            return;
        }

        Scheduler.getScheduler().runSync(Common.plugin, new BackupProcessStarter(afterBackup, sender));
    }
}
