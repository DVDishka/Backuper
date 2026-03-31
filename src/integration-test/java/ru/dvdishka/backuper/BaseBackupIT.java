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
        backupTest(100000, false);
    }

    @Test
    public void smallZipBackupTest() throws IOException, InterruptedException {
        backupTest(100000, true);
    }

    @Test
    public void bigFolderBackupTest() throws IOException, InterruptedException {
        backupTest(10_000_000_000L, false);
    }

    @Test
    public void bigZipBackupTest() throws IOException, InterruptedException {
        backupTest(10_000_000_000L, true);
    }

    public void backupTest(long targetBackupSize, boolean zipArchive) throws IOException, InterruptedException {
        File testDir = new File("%s-%s-%sbyte-backupTest".formatted(storage.getId(), zipArchive ? "zip" : "folder", targetBackupSize));
        testDir.mkdirs();
        testDir.deleteOnExit();

        // Generate one heavy file
        File bigTestFile = new File(testDir, "big.test");
        bigTestFile.createNewFile();
        bigTestFile.deleteOnExit();
        ITUtils.generateRandomFile(bigTestFile, targetBackupSize - 45);

        File currentDir = testDir;
        // Generate light file structure
        for (int i = 0; i < 10; i++) {
            currentDir = currentDir.toPath().resolve("0").toFile();
            currentDir.mkdirs();
            currentDir.deleteOnExit();

            for (int j = 1; j <= i; j++) {
                File testFile = currentDir.toPath().resolve(String.valueOf(j)).toFile();
                testFile.createNewFile();
                testFile.deleteOnExit();

                ITUtils.generateRandomFile(testFile, 1);
            }
        }

        config.set("storages.%s.zipArchive".formatted(storage.getId()), zipArchive);
        config.set("backup.addDirectoryToBackup", List.of(testDir.getName()));
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
            Backup backup = backups.getFirst();

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