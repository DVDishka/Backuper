package ru.dvdishka.backuper.backend.autobackup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.task.TaskManager;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.util.ArrayList;
import java.util.List;

public class AutoBackupJob {

    /***
     * Controls if auto backup is enabled and if so, starts a new backup task.
     */
    public void executeBackup() {
        if (!Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup()) return;

        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            List<Storage> autoBackupStorages = Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage.getConfig().isAutoBackup()).toList();
            Task backupTask = new BackupTask(autoBackupStorages, Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup(), true);

            List<String> permissions = new ArrayList<>(){};
            permissions.addAll(autoBackupStorages.stream().map(Permission.BACKUP::getPermission).toList());
            if ("RESTART".equals(Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup())) {
                permissions.add(Permission.RESTART.getPermission());
            }
            if ("STOP".equals(Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup())) {
                permissions.add(Permission.STOP.getPermission());
            }

            if (TaskManager.Result.LOCKED.equals(Backuper.getInstance().getTaskManager().startTask(backupTask, Bukkit.getConsoleSender(), permissions))) {
                Backuper.getInstance().getLogManager().warn("Failed to start an Auto Backup task. Blocked by another operation", Bukkit.getConsoleSender());
            }
        });
    }

    /***
     * Controls if alert is enabled and if so, sends an alert message to all players with appropriate permission.
     */
    public void executeAlert(long timeSeconds, String afterBackup) {
        if (Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart() == -1) return;
        boolean restart = false;

        if (afterBackup.equals("STOP")) {
            Backuper.getInstance().getLogManager().log(Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupRestartMessage().formatted(timeSeconds));
            restart = true;
        }
        if (afterBackup.equals("RESTART")) {
            Backuper.getInstance().getLogManager().log(Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupRestartMessage().formatted(timeSeconds));
            restart = true;
        }
        if (afterBackup.equals("NOTHING")) {
            Backuper.getInstance().getLogManager().log(Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupMessage().formatted(timeSeconds));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (!player.hasPermission(Permission.ALERT.getPermission())) {
                continue;
            }

            if (restart || !Backuper.getInstance().getConfigManager().getServerConfig().isAlertOnlyServerRestart()) {

                Component header = Component.empty();

                header = header
                        .append(Component.text("Backup Alert")
                                .decorate(TextDecoration.BOLD));

                Component message = Component.empty();

                message = message
                        .append(Component.text((restart ? Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupRestartMessage() :
                                Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupMessage()).formatted(timeSeconds)));

                UIUtils.sendFramedMessage(header, message, 15, player);
                UIUtils.notificationSound(player);
            }
        }
    }
}
