package ru.dvdishka.backuper.backend.autobackup;

import lombok.Getter;
import org.quartz.CronTrigger;
import ru.dvdishka.backuper.Backuper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class AutoBackupCronJobScheduler implements AutoBackupJobScheduler {

    @Getter
    private final AutoBackupScheduleManager autoBackupScheduleManager;
    private CronTrigger cronTrigger;
    private boolean firstAlert = true;

    public AutoBackupCronJobScheduler(AutoBackupScheduleManager autoBackupScheduleManager) {
        this.autoBackupScheduleManager = autoBackupScheduleManager;
    }

    public void init() {
        this.cronTrigger = Backuper.getInstance().getScheduleManager()
                .runCronScheduledJob(AutoBackupCronJob.class, "backup", "auto", Backuper.getInstance().getConfigManager().getBackupConfig().getAutoBackupCron());
    }

    @Override
    public long getNextBackupDelaySeconds() {
        Date nextFireTime = firstAlert ? cronTrigger.getNextFireTime() : cronTrigger.getFireTimeAfter(cronTrigger.getNextFireTime());
        return nextFireTime.getTime() / 1000 - LocalDateTime.now().toEpochSecond(ZoneOffset.ofHours(nextFireTime.getTimezoneOffset() / 60 * -1));
    }

    @Override
    public void scheduleNextBackupAlert() {
        Backuper.getInstance().getScheduleManager().runGlobalRegionDelayed(Backuper.getInstance(), () -> {
            getAutoBackupScheduleManager().getAutoBackupJob().executeAlert(getNextAlertMessageSeconds(), Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup());
            firstAlert = false;
        }, getNextBackupAlertDelaySeconds() * 20L);
    }
}
