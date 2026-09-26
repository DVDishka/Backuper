package ru.dvdishka.backuper.backend.autobackup;

import org.quartz.JobExecutionContext;
import ru.dvdishka.backuper.backend.ScheduleManager;

import java.util.concurrent.RejectedExecutionException;

public class AutoBackupCronJob implements org.quartz.Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        var data = jobExecutionContext.getJobDetail().getJobDataMap();
        var scheduler = (ScheduleManager) data.get("backuperScheduler");
        var autoBackup = (AutoBackupScheduleManager) data.get("backuperAutoBackup");
        try {
            // Bind the Quartz callback to the generation that registered it and track its whole lifetime.
            scheduler.runAsync(() -> autoBackup.getAutoBackupJobScheduler().executeBackupAndScheduleNextAlert());
        } catch (RejectedExecutionException ignored) {
            // This generation was stopped or reloaded before the callback could be dispatched.
        }
    }
}
