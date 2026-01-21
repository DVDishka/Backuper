package ru.dvdishka.backuper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public abstract class BaseBackupIT extends BaseTest {

    protected Storage storage;

    @Test
    public void smallFolderTest() throws IOException, InterruptedException {
        backupTest(10000, false);
    }

    @Test
    public void smallZipBackupTest() throws IOException, InterruptedException {
        backupTest(10000, true);
    }

    @Test
    public void bigFolderBackupTest() throws IOException, InterruptedException {
        backupTest(10000000000L, false);
    }

    @Test
    public void bigZipBackupTest() throws IOException, InterruptedException {
        backupTest(10000000000L, true);
    }

    public void backupTest(long targetBackupSize, boolean zipArchive) throws IOException, InterruptedException {
        File file = new File("%s-%s-%sbyte-backupTest.test".formatted(storage.getId(), zipArchive ? "zip" : "folder", targetBackupSize));
        ITUtils.generateRandomFile(file, targetBackupSize);

        config.set("storages.%s.zipArchive".formatted(storage.getId()), zipArchive);
        config.set("backup.addDirectoryToBackup", List.of(file.getName()));
        reload();
        storage = Backuper.getInstance().getStorageManager().getStorage(storage.getId()); // We should update storage after reloading

        ITUtils.deleteAllBackups(storage);

        try {
            Task backupTask = new BackupTask(List.of(storage), "NOTHING", false);
            Backuper.getInstance().getTaskManager().startTask(backupTask,
                    Backuper.getInstance().getServer().getConsoleSender(),
                    List.of(Permission.BACKUP.getPermission(storage)));

            Thread.sleep(Duration.ofSeconds(6)); // wait for backupList cache to expire

            List<Backup> backups = storage.getBackupManager().getBackupList();

            Assertions.assertEquals(1, backups.size());
            Backup backup = backups.get(0);

            Assertions.assertEquals(zipArchive ? Backup.BackupFileType.ZIP : Backup.BackupFileType.DIR, backup.getFileType());

            long delta = zipArchive ? targetBackupSize / 2 + 1000 : 1000; // Zip size may vary

            long cachedBackupSize = backup.getByteSize();
            Assertions.assertEquals(backup.calculateByteSize(), cachedBackupSize); // Ensure cached size is correct

            Assertions.assertEquals(targetBackupSize, cachedBackupSize, delta); // Delta for zip and world size
        } finally {
            ITUtils.deleteAllBackups(storage);
        }
    }
}