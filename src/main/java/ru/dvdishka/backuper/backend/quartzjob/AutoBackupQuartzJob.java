package ru.dvdishka.backuper.backend.quartzjob;

import org.bukkit.Bukkit;
import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.task.TaskManager;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class AutoBackupQuartzJob implements org.quartz.Job {

    public void init() {
        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            Backuper.getInstance().getLogManager().log("Initializing auto backup...");

            CronTrigger autoBackupJobTrigger = Backuper.getInstance().getScheduleManager().runCronScheduledJob(AutoBackupQuartzJob.class, "backup", "auto", Backuper.getInstance().getConfigManager().getBackupConfig().getAutoBackupCron());
            scheduleNextBackupAlert(autoBackupJobTrigger); // Prepare alert for backup above

            Backuper.getInstance().getLogManager().log("Auto backup initialization completed");
        });
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        if (Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup()) {

            scheduleNextBackupAlert(jobExecutionContext.getTrigger()); // Prepare alert for next backup

            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                Task backupTask = new BackupTask(Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage.getConfig().isEnabled() && storage.getConfig().isAutoBackup()).toList(),
                        Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup(), true);
                List<Permissions> permissions = new ArrayList<>(){};
                permissions.add(Permissions.BACKUP);
                if ("RESTART".equals(Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup())) {
                    permissions.add(Permissions.RESTART);
                }
                if ("STOP".equals(Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup())) {
                    permissions.add(Permissions.STOP);
                }
                if (TaskManager.Result.LOCKED.equals(Backuper.getInstance().getTaskManager().startTask(backupTask, Bukkit.getConsoleSender(), permissions))) {
                    Backuper.getInstance().getLogManager().warn("Failed to start an Auto Backup task. Blocked by another operation", Bukkit.getConsoleSender());
                }
            });
        }
    }

    public void scheduleNextBackupAlert(Trigger cronTrigger) {
        if (Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart() != -1) {

            long delay = cronTrigger.getNextFireTime().getTime() / 1000 - LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(cronTrigger.getNextFireTime().getTimezoneOffset() / 60 * -1)); // Calculating delay to next backup
            delay = max((delay - Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart()) * 20, 1); // Subtracting alert pre-delay from the new backup's delay
            long alertTime = min(Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart(), delay); // Used for notification only

            Backuper.getInstance().getScheduleManager().runSyncDelayed(Backuper.getInstance(), () -> {
                UIUtils.sendBackupAlert(alertTime, Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup());
            }, delay);
        }
    }
}
