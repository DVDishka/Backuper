package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.common.BackupTask;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.primitives.Longs.min;
import static java.lang.Math.max;

public class BackupCommand extends Command {

    private String afterBackup = "NOTHING";
    private long delay;
    private boolean isLocal = false;
    private boolean isFtp = false;
    private boolean isSftp = false;

    public BackupCommand(CommandSender sender, CommandArguments args, String afterBackup) {

        super(sender, args);
        this.afterBackup = afterBackup;
        this.delay = (long) args.getOrDefault("delaySeconds", 1L);

        String storageString = ((String) args.get("storage"));
        String[] storages = storageString.split("-");

        for (String s : storages) {
            if (s.equals("local")) {
                isLocal = true;
            }
            if (s.equals("ftp")) {
                isFtp = true;
            }
            if (s.equals("sftp")) {
                isSftp = true;
            }
        }
    }

    public BackupCommand(CommandSender sender, CommandArguments args) {

        super(sender, args);
        this.delay = (long) args.getOrDefault("delaySeconds", 1L);

        String storageString = ((String) args.get("storage"));
        String[] storages = storageString.split("-");

        for (String s : storages) {
            if (s.equals("local")) {
                isLocal = true;
            }
            if (s.equals("ftp")) {
                isFtp = true;
            }
            if (s.equals("sftp")) {
                isSftp = true;
            }
        }
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

        if (!isLocal && !isFtp && !isSftp) {
            cancelSound();
            returnFailure("Wrong storage types!");
            return;
        }

        if (isLocal && !Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled!");
            return;
        }

        if (isFtp && !FtpUtils.checkConnection(sender)) {
            cancelSound();
            returnFailure("FTP(S) storage is disabled or unavailable!");
            return;
        }

        if (isSftp && !SftpUtils.checkConnection(sender)) {
            cancelSound();
            returnFailure("SFTP storage is disabled or unavailable!");
            return;
        }

        buttonSound();

        if (Config.getInstance().getAlertTimeBeforeRestart() != -1) {

            Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {

                UIUtils.sendBackupAlert(min(Config.getInstance().getAlertTimeBeforeRestart(), delay), afterBackup);

            }, max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1));
        }

        List<Permissions> backupPermissions = new ArrayList<>();
        backupPermissions.add(Permissions.BACKUP);
        if (afterBackup.equals("STOP")) {
            backupPermissions.add(Permissions.STOP);
        }
        if (afterBackup.equals("RESTART")) {
            backupPermissions.add(Permissions.RESTART);
        }

        Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {

            StatusCommand.sendTaskStartedMessage("Backup", sender);

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
                new BackupTask(afterBackup, false, isLocal, isFtp, isSftp, true, backupPermissions, sender).run();
                sendMessage("Backup task completed");
            });

        }, delay * 20);

        if (arguments.get("delaySeconds") != null) {

            returnSuccess("Backup task will be started in " + delay + " seconds");

            if (!(sender instanceof ConsoleCommandSender)) {
                Logger.getLogger().log("Backup task will be started in " + delay + " seconds");
            }
        }
    }
}
