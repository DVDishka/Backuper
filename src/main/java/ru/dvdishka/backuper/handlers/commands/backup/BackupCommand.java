package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.backend.utils.Logger;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.backend.utils.Common;
import ru.dvdishka.backuper.backend.utils.Scheduler;

import static com.google.common.primitives.Longs.min;
import static java.lang.Math.max;

public class BackupCommand extends Command {

    private String afterBackup = "NOTHING";
    private long delay = 1;

    public BackupCommand(CommandSender sender, CommandArguments args, String afterBackup) {

        super(sender, args);
        this.afterBackup = afterBackup;
        this.delay = (long) args.getOrDefault("delay", 1L);
    }

    public BackupCommand(CommandSender sender, CommandArguments args) {

        super(sender, args);
        this.delay = (long) args.getOrDefault("delay", 1L);
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

        if (Config.getInstance().getAlertTimeBeforeRestart() != -1) {

            Scheduler.getScheduler().runSyncDelayed(Common.plugin, () -> {

                Backup.sendBackupAlert(min(Config.getInstance().getAlertTimeBeforeRestart(), delay), afterBackup);

            }, max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1));
        }

        Scheduler.getScheduler().runSyncDelayed(Common.plugin, new BackupProcessStarter(afterBackup, sender), delay * 20);

        if (arguments.get("delay") != null) {
            returnSuccess("Backup process will be started in " + delay + " seconds");

            if (!(sender instanceof ConsoleCommandSender)) {
                Logger.getLogger().log("Backup process will be started in " + delay + " seconds");
            }
        }
    }
}
