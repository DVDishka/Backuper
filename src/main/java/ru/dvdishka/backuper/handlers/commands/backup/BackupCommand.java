package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.backend.utils.Common;
import ru.dvdishka.backuper.backend.utils.Scheduler;

import static com.google.common.primitives.Longs.min;
import static java.lang.Math.max;

public class BackupCommand extends Command {

    private String afterBackup = "NOTHING";
    private long delay;

    public BackupCommand(CommandSender sender, CommandArguments args, String afterBackup) {

        super(sender, args);
        this.afterBackup = afterBackup;
        this.delay = (long) args.getOrDefault("delay", 1);
    }

    public BackupCommand(CommandSender sender, CommandArguments args) {

        super(sender, args);
    }

    public void execute() {

        if (ru.dvdishka.backuper.backend.utils.Backup.isBackupBusy) {
            returnFailure("Blocked by another operation!");
            return;
        }

        if (delay < 1) {
            returnFailure("Delay must be > 0!");
            return;
        }

        if (Config.getInstance().getAlertTimeBeforeRestart() != -1 && !afterBackup.equals("NOTHING")) {

            Scheduler.getScheduler().runSyncDelayed(Common.plugin, () -> {

                Backup.sendBackupAlert(min(Config.getInstance().getAlertTimeBeforeRestart(), delay));

            }, max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1));
        }

        Scheduler.getScheduler().runSyncDelayed(Common.plugin, new BackupProcessStarter(afterBackup, sender), delay * 20);
    }
}
