package ru.dvdishka.backuper.backend.quartzjob;

import org.bukkit.Bukkit;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BackupTask;
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

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        if (Config.getInstance().isAutoBackup()) {

            scheduleNextBackupAlert(jobExecutionContext.getTrigger()); // Prepare alert for next backup

            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                AsyncTask backupTask = new BackupTask(Config.getInstance().getAfterBackup(), true);
                List<Permissions> permissions = new ArrayList<>(){};
                permissions.add(Permissions.BACKUP);
                if ("RESTART".equals(Config.getInstance().getAfterBackup())) {
                    permissions.add(Permissions.RESTART);
                }
                if ("STOP".equals(Config.getInstance().getAfterBackup())) {
                    permissions.add(Permissions.STOP);
                }
                if (TaskManager.Result.LOCKED.equals(Backuper.getInstance().getTaskManager().startTask(backupTask, Bukkit.getConsoleSender(), permissions))) {
                    Backuper.getInstance().getLogManager().warn("Failed to start an Auto Backup task. Blocked by another operation", Bukkit.getConsoleSender());
                }
            });
        }
    }

    public static void scheduleNextBackupAlert(Trigger cronTrigger) {
        if (Config.getInstance().getAlertTimeBeforeRestart() != -1) {

            long delay = cronTrigger.getNextFireTime().getTime() / 1000 - LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(cronTrigger.getNextFireTime().getTimezoneOffset() / 60 * -1)); // Calculating delay to next backup
            delay = max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1); // Subtracting alert pre-delay from new backup's delay
            long alertTime = min(Config.getInstance().getAlertTimeBeforeRestart(), delay); // Used for notification only

            Backuper.getInstance().getScheduleManager().runSyncDelayed(Backuper.getInstance(), () -> {
                UIUtils.sendBackupAlert(alertTime, Config.getInstance().getAfterBackup());
            }, delay);
        }
    }
}
