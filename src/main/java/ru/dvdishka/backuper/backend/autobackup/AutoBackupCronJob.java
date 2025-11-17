package ru.dvdishka.backuper.backend.autobackup;

import org.quartz.JobExecutionContext;
import ru.dvdishka.backuper.Backuper;

public class AutoBackupCronJob implements org.quartz.Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        Backuper.getInstance().getAutoBackupScheduleManager().getAutoBackupJobScheduler().executeBackupAndScheduleNextAlert();
    }
}
