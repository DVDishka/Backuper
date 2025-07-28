package ru.dvdishka.backuper.backend.quartzjob;

import org.bukkit.Bukkit;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.common.BackupTask;
import ru.dvdishka.backuper.backend.tasks.common.DeleteBrokenBackupsTask;
import ru.dvdishka.backuper.backend.tasks.common.DeleteOldBackupsTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class AutoBackupQuartzJob implements org.quartz.Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        // AUTO BACKUP PERMISSION LIST CREATION
        List<Permissions> autoBackupPermissions = new ArrayList<>();
        {
            autoBackupPermissions.add(Permissions.BACKUP);
            if (Config.getInstance().getAfterBackup().equals("STOP")) {
                autoBackupPermissions.add(Permissions.STOP);
            }
            if (Config.getInstance().getAfterBackup().equals("RESTART")) {
                autoBackupPermissions.add(Permissions.RESTART);
            }
        }

        Logger.getLogger().log("Deleting old backups...");
        StatusCommand.sendTaskStartedMessage("DeleteOldBackups", Bukkit.getConsoleSender());
        new DeleteOldBackupsTask(true, List.of(Permissions.BACKUP), Bukkit.getConsoleSender()).run();

        if (Config.getInstance().isDeleteBrokenBackups()) {
            Logger.getLogger().log("Deleting broken backups...");
            StatusCommand.sendTaskStartedMessage("DeleteBrokenBackups", Bukkit.getConsoleSender());
            new DeleteBrokenBackupsTask(true, List.of(Permissions.BACKUP), Bukkit.getConsoleSender()).run();
        }

        if (Config.getInstance().isAutoBackup()) {

            scheduleNextBackupAlert(jobExecutionContext.getTrigger()); // Prepare alert for next backup

            Scheduler.getInstance().runAsync(Utils.plugin, () -> {
                if (!Backuper.isLocked()) {
                    new BackupTask(Config.getInstance().getAfterBackup(), true, true, autoBackupPermissions, null).run();
                } else {
                    Logger.getLogger().warn("Failed to start an Auto Backup task. Blocked by another operation", Bukkit.getConsoleSender());
                }
            });
        }
    }

    public static void scheduleNextBackupAlert(Trigger cronTrigger) {
        if (Config.getInstance().getAlertTimeBeforeRestart() != -1) {

            long delay = cronTrigger.getNextFireTime().getTime() / 1000 - LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(cronTrigger.getNextFireTime().getTimezoneOffset() / 60 * -1)); // Calculating delay to next backup
            delay = max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1); // Subtracting alert pre-delay from new backup's delay
            long alertTime = min(Config.getInstance().getAlertTimeBeforeRestart(), delay); // Used for notification only

            Scheduler.getInstance().runSyncDelayed(Utils.plugin, () -> {
                UIUtils.sendBackupAlert(alertTime, Config.getInstance().getAfterBackup());
            }, delay);
        }
    }
}
