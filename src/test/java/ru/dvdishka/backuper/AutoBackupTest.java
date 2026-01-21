package ru.dvdishka.backuper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

        Assertions.assertEquals(requiredDelay.getSeconds(), realAutoBackupDelay, 1L);

        // Test alert
        LocalDateTime requiredNextAlert = requiredNextBackup.minusSeconds(60);
        Duration requiredNextAlertDelay = Duration.between(LocalDateTime.now(), requiredNextAlert);
        long realNextAlertDelay = Backuper.getInstance().getAutoBackupScheduleManager().getAutoBackupJobScheduler().getNextAlertDelaySeconds();

        Assertions.assertEquals(requiredNextAlertDelay.isPositive() ? requiredNextAlertDelay.getSeconds() : 0, realNextAlertDelay, 3);
    }

    @Test
    public void testPeriodDelay() throws IOException {
        Backuper.getInstance().getConfigManager().updateLastBackup();
        config.set("backup.autoBackupCron", "");
        config.set("backup.autoBackupPeriod", 2880);
        config.set("server.alertTimeBeforeRestart", 60);
        config.set("server.alertOnlyServerRestart", false);
        reload();

        LocalDateTime requiredNextBackup = LocalDateTime.now().plusMinutes(2880);
        Duration requiredNextBackupDelay = Duration.between(LocalDateTime.now(), requiredNextBackup);
        long realNextBackupDelay = Backuper.getInstance().getAutoBackupScheduleManager().getAutoBackupJobScheduler().getNextBackupDelaySeconds();

        Assertions.assertEquals(requiredNextBackupDelay.getSeconds(), realNextBackupDelay, 1);

        // Test alert
        LocalDateTime requiredNextAlert = requiredNextBackup.minusSeconds(60);
        Duration requiredNextAlertDelay = Duration.between(LocalDateTime.now(), requiredNextAlert);
        long realNextAlertDelay = Backuper.getInstance().getAutoBackupScheduleManager().getAutoBackupJobScheduler().getNextAlertDelaySeconds();

        Assertions.assertEquals(requiredNextAlertDelay.getSeconds(), realNextAlertDelay, 1);
    }
}
