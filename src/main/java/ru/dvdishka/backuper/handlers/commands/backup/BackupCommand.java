package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.primitives.Longs.min;

public class BackupCommand extends Command {

    private String afterBackup = "NOTHING";
    private long delay;
    private boolean isLocal = false;
    private boolean isFtp = false;
    private boolean isSftp = false;
    private boolean isGoogleDrive = false;

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
            if (s.equals("googleDrive")) {
                isGoogleDrive = true;
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
            if (s.equals("googleDrive")) {
                isGoogleDrive = true;
            }
        }
    }

    public void execute() {

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        if (delay < 1) {
            cancelSound();
            returnFailure("Delay must be > 0!");
            return;
        }

        if (!isLocal && !isFtp && !isSftp && !isGoogleDrive) {
            cancelSound();
            returnFailure("Wrong storage types!");
            return;
        }

        if (isLocal && !ConfigManager.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled!");
            return;
        }

        if (isFtp && !FtpUtils.checkConnection(sender)) {
            cancelSound();
            return;
        }

        if (isSftp && !SftpUtils.checkConnection(sender)) {
            cancelSound();
            return;
        }

        if (isGoogleDrive && (!ConfigManager.getInstance().getGoogleDriveConfig().isEnabled() || !GoogleDriveUtils.checkConnection())) {
            cancelSound();
            return;
        }

        buttonSound();

        if (ConfigManager.getInstance().getAlertTimeBeforeRestart() != -1) {

            Backuper.getInstance().getScheduleManager().runSyncDelayed(Backuper.getInstance(), () -> {

                UIUtils.sendBackupAlert(min(ConfigManager.getInstance().getAlertTimeBeforeRestart(), delay), afterBackup);

            }, max((delay - ConfigManager.getInstance().getAlertTimeBeforeRestart()) * 20, 1));
        }

        List<Permissions> backupPermissions = new ArrayList<>();
        backupPermissions.add(Permissions.BACKUP);
        if (afterBackup.equals("STOP")) {
            backupPermissions.add(Permissions.STOP);
        }
        if (afterBackup.equals("RESTART")) {
            backupPermissions.add(Permissions.RESTART);
        }

        Backuper.getInstance().getScheduleManager().runSyncDelayed(Backuper.getInstance(), () -> {

            AsyncTask task = new BackupTask(afterBackup, false, isLocal, isFtp, isSftp, isGoogleDrive);
            Backuper.getInstance().getTaskManager().startTaskAsync(task, sender, backupPermissions);

        }, delay * 20);

        if (arguments.get("delaySeconds") != null) {

            returnSuccess("Backup task will be started in %s seconds".formatted(delay));

            if (!(sender instanceof ConsoleCommandSender)) {
                Backuper.getInstance().getLogManager().log("Backup task will be started in %s seconds".formatted(delay));
            }
        }
    }
}
