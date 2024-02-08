package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.handlers.commands.common.CommandInterface;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.common.Scheduler;

public class Backup implements CommandInterface {

    private String afterBackup = "NOTHING";

    public Backup() {}

    public Backup(String afterBackup) {

        this.afterBackup = afterBackup;
    }

    public void execute(CommandSender sender, CommandArguments args) {

        if (ru.dvdishka.backuper.back.common.Backup.isBackupBusy) {
            returnFailure("Blocked by another operation!", sender);
            return;
        }

        Scheduler.getScheduler().runSync(Common.plugin, new BackupProcessStarter(afterBackup, sender));
    }
}
