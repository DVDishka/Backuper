package ru.dvdishka.backuper.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.BaseTest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AutoBackupTest extends BaseTest {

    @Test
    public void testCronDelay() throws IOException {
        config.set("backup.autoBackupCron", "0 0 10 1/1 * ? *");
        reload();

        LocalDateTime requiredNextBackup = LocalDateTime.of(LocalDateTime.now().toLocalDate(), LocalTime.of(10, 0))
                .plusDays(LocalDateTime.now().getHour() < 10 ? 0 : 1);
        Duration requiredDelay = Duration.between(LocalDateTime.now(), requiredNextBackup);

        long realAutoBackupDelay = Backuper.getInstance().getAutoBackupScheduleManager().getAutoBackupJobScheduler().getNextBackupDelaySeconds();

        Assertions.assertEquals(requiredDelay.getSeconds(), realAutoBackupDelay, 5L);
    }

    @Test
    public void testPeriodDelay() throws IOException {
        Backuper.getInstance().getConfigManager().updateLastBackup();
        config.set("backup.autoBackupCron", "");
        config.set("backup.autoBackupPeriod", 2880);
        reload();

        LocalDateTime requiredNextBackup = LocalDateTime.now().plusMinutes(2880);
        Duration requiredDelay = Duration.between(LocalDateTime.now(), requiredNextBackup);

        long realAutoBackupDelay = Backuper.getInstance().getAutoBackupScheduleManager().getAutoBackupJobScheduler().getNextBackupDelaySeconds();

        Assertions.assertEquals(requiredDelay.getSeconds(), realAutoBackupDelay, 5L);
    }
}
