package ru.dvdishka.backuper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BackupTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.util.Utils;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class LocalBackupIT extends BaseTest {

    @Test
    public void smallFolderLocalBackupTest() throws IOException {
        localBackupTest(10000, false);
    }

    @Test
    public void smallZipLocalBackupTest() throws IOException {
        localBackupTest(10000, true);
    }

    @Test
    public void bigFolderLocalBackupTest() throws IOException {
        localBackupTest(10000000000L, false);
    }

    @Test
    public void bigZipLocalBackupTest() throws IOException {
        localBackupTest(10000000000L, true);
    }

    public void localBackupTest(long targetBackupSize, boolean zipArchive) throws IOException {
        File file = new File("smallLocalBackupTest.test");
        ITUtils.generateRandomFile(file, targetBackupSize);

        config.set("storages.local.zipArchive", zipArchive);
        config.set("backup.addDirectoryToBackup", List.of("smallLocalBackupTest.test"));
        config.save(configFile);
        reload();

        Storage localStorage = Backuper.getInstance().getStorageManager().getStorage("local");
        ITUtils.deleteAllBackups(localStorage);

        try {
            Task backupTask = new BackupTask(List.of(localStorage), "NOTHING", false);
            Backuper.getInstance().getTaskManager().startTask(backupTask,
                    Backuper.getInstance().getServer().getConsoleSender(),
                    List.of(Permission.BACKUP.getPermission(localStorage)));

            File[] backupsFiles = new File(localStorage.getConfig().getBackupsFolder()).listFiles();
            Assertions.assertNotNull(backupsFiles);

            Assertions.assertEquals(1, backupsFiles.length);
            File backupFile = backupsFiles[0];

            long delta = zipArchive ? targetBackupSize / 2 : 1000;
            Assertions.assertEquals(targetBackupSize, Utils.getFileFolderByteSize(backupFile), delta);
        } finally {
            ITUtils.deleteAllBackups(localStorage);
        }
    }
}