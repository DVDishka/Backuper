package ru.dvdishka.backuper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

public class SkipDuplicateBackupIT extends BaseTest {

    @BeforeEach
    public void setUp() throws IOException {
        config.set("backup.autoBackup", true);
        config.set("backup.skipDuplicateBackup", true);
        config.set("backup.autoBackupCron", "");
        config.set("backup.autoBackupPeriod", 1);
        Backuper.getInstance().getConfigManager().updateLastBackup();
        reload();
    }

    @Test
    public void testSkipDuplicateBackup() throws InterruptedException {
        long lastBackup = Backuper.getInstance().getConfigManager().getLastBackup();
        Thread.sleep(Duration.ofSeconds(90));

        Assertions.assertFalse(Backuper.getInstance().getTaskManager().isLocked());
        Assertions.assertEquals(lastBackup, Backuper.getInstance().getConfigManager().getLastBackup());
    }
}
