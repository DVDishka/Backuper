package ru.dvdishka.backuper.backend.autobackup;

import ru.dvdishka.backuper.Backuper;

import static java.lang.Math.max;
import static java.lang.Math.min;

public interface AutoBackupJobScheduler {

    void init();

    long getNextBackupDelaySeconds();

    default long getNextBackupAlertDelaySeconds() {
        long delay = getNextBackupDelaySeconds(); // Calculating delay to next backup
        return max((delay - Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart()) * 20, 1L); // Subtracting alert pre-delay from the new backup's delay
    }

    default long getNextAlertMessageSeconds() {
        return min(Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart(), getNextBackupDelaySeconds());
    }

    default void scheduleNextBackupAlert() {
        Backuper.getInstance().getScheduleManager().runGlobalRegionDelayed(Backuper.getInstance(), () -> {
            getAutoBackupScheduleManager().getAutoBackupJob().executeAlert(getNextAlertMessageSeconds(), Backuper.getInstance().getConfigManager().getBackupConfig().getAfterBackup());
        }, getNextBackupAlertDelaySeconds() * 20L);
    }

    default void executeBackupAndScheduleNextAlert() {
        getAutoBackupScheduleManager().getAutoBackupJob().executeBackup();
        scheduleNextBackupAlert(); // Must be called after backup is done
    }

    AutoBackupScheduleManager getAutoBackupScheduleManager();
}
