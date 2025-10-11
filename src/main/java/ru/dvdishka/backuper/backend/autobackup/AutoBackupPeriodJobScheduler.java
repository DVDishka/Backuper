package ru.dvdishka.backuper.backend.autobackup;

import lombok.Getter;
import ru.dvdishka.backuper.Backuper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.lang.Long.max;

public class AutoBackupPeriodJobScheduler implements AutoBackupJobScheduler {

    @Getter
    private final AutoBackupScheduleManager autoBackupScheduleManager;

    public AutoBackupPeriodJobScheduler(AutoBackupScheduleManager autoBackupScheduleManager) {
        this.autoBackupScheduleManager = autoBackupScheduleManager;
    }

    @Override
    public void init() {
        Backuper.getInstance().getScheduleManager().runGlobalRegionRepeatingTask(Backuper.getInstance(), () -> {
            Backuper.getInstance().getScheduleManager().runAsync(this::executeBackupAndScheduleNextAlert);
        }, getNextBackupDelaySeconds() * 20L, Backuper.getInstance().getConfigManager().getBackupConfig().getAutoBackupPeriod() * 60L * 20L);
    }

    @Override
    public long getNextBackupDelaySeconds() {
        return max(Backuper.getInstance().getConfigManager().getBackupConfig().getAutoBackupPeriod() * 60L -
                (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - Backuper.getInstance().getConfigManager().getLastBackup()),
                1L); // No matter what offset is used because we are using only delta, not the absolute time
    }
}
