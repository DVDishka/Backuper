package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.backup.BackupTask;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

import static com.google.common.primitives.Longs.min;
import static java.lang.Math.max;

public class BackupCommand extends Command {

    private String afterBackup = "NOTHING";
    private long delay = 1;

    public BackupCommand(CommandSender sender, CommandArguments args, String afterBackup) {

        super(sender, args);
        this.afterBackup = afterBackup;
        this.delay = (long) args.getOrDefault("delaySeconds", 1L);
    }

    public BackupCommand(CommandSender sender, CommandArguments args) {

        super(sender, args);
        this.delay = (long) args.getOrDefault("delaySeconds", 1L);
    }

    public void execute() {

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        if (delay < 1) {
            cancelSound();
            returnFailure("Delay must be > 0!");
            return;
        }

        buttonSound();

        if (Config.getInstance().getAlertTimeBeforeRestart() != -1) {

            Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {

                LocalBackup.sendBackupAlert(min(Config.getInstance().getAlertTimeBeforeRestart(), delay), afterBackup);

            }, max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1));
        }

        Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {

            StatusCommand.sendTaskStartedMessage("Backup", sender);

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
                new BackupTask(afterBackup, false, true, sender).run();
                sendMessage("Backup task completed");
            });

        }, delay * 20);

        if (arguments.get("delaySeconds") != null) {

            returnSuccess("Backup process will be started in " + delay + " seconds");

            if (!(sender instanceof ConsoleCommandSender)) {
                Logger.getLogger().log("Backup process will be started in " + delay + " seconds");
            }
        }
    }
}
