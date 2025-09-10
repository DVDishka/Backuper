package ru.dvdishka.backuper.handlers.commands.backup;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.primitives.Longs.min;
import static java.lang.Long.max;

public class BackupCommand extends Command {

    private final String afterBackup;
    private final long delay;
    private final List<Storage> storages = new ArrayList<>();

    public BackupCommand(CommandSender sender, CommandArguments args, String afterBackup) {
        super(sender, args);
        this.afterBackup = afterBackup.toUpperCase();
        this.delay = (long) args.getOrDefault("delaySeconds", 1L);
    }

    @Override
    public boolean check() {
        if (Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("Blocked by another operation");
            return false;
        }
        if (delay < 1) {
            returnFailure("Delay must be > 0!");
            return false;
        }
        boolean storageParseFailure = false;
        for (String storageId : ((String) this.arguments.get("storage")).split("-")) {
            if (storageId.isEmpty()) continue;
            Storage storage = Backuper.getInstance().getStorageManager().getStorage(storageId);
            if (storage == null) {
                returnFailure("Wrong storage name %s".formatted(storageId));
                storageParseFailure = true;
            }
            else if (!storage.checkConnection(sender)) {
                returnFailure("Failed to connect to %s storage".formatted(storage.getId()));
                storageParseFailure = true;
            }
        }
        if (storageParseFailure) return false;
        if (!storages.stream().map(Permission.BACKUP::getPermission).allMatch(sender::hasPermission) || afterBackup.equals("STOP") && !sender.hasPermission(Permission.STOP.getPermission()) || afterBackup.equals("RESTART") && !sender.hasPermission(Permission.RESTART.getPermission())) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }

        return true;
    }

    @Override
    public void run() {

        if (Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart() != -1) {
            Backuper.getInstance().getScheduleManager().runGlobalRegionDelayed(Backuper.getInstance(), () -> {
                UIUtils.sendBackupAlert(min(Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart(), delay), afterBackup);
            }, max((delay - Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart()) * 20, 1));
        }

        List<String> backupPermissions = new ArrayList<>();
        for (Storage storage : this.storages) {
            backupPermissions.add(Permission.BACKUP.getPermission(storage));
        }
        if (afterBackup.equals("STOP")) {
            backupPermissions.add(Permission.STOP.getPermission());
        }
        if (afterBackup.equals("RESTART")) {
            backupPermissions.add(Permission.RESTART.getPermission());
        }

        Backuper.getInstance().getScheduleManager().runGlobalRegionDelayed(Backuper.getInstance(), () -> {
            Task task = new BackupTask(this.storages, afterBackup, false);
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
