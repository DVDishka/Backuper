package ru.dvdishka.backuper.backend.autobackup;

import lombok.Getter;
import ru.dvdishka.backuper.Backuper;

@Getter
public class AutoBackupScheduleManager {

    private AutoBackupJob autoBackupJob;
    private AutoBackupJobScheduler autoBackupJobScheduler;

    public void init() {
        this.autoBackupJob = new AutoBackupJob();
        if (Backuper.getInstance().getConfigManager().getBackupConfig().getAutoBackupCron() != null) {
            this.autoBackupJobScheduler = new AutoBackupCronJobScheduler(this);
        } else {
            this.autoBackupJobScheduler = new AutoBackupPeriodJobScheduler(this);
        }

        if (!Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup()) return;

        Backuper.getInstance().getLogManager().log("Initializing auto backup...");

        autoBackupJobScheduler.init();
        autoBackupJobScheduler.scheduleNextBackupAlert(); // Prepare alert for the first backup

        Backuper.getInstance().getLogManager().log("Auto backup initialization completed");
    }

    public void destroy() {
        autoBackupJob = null;
        autoBackupJobScheduler = null;
    }
}
