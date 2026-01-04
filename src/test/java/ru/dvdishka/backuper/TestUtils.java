package ru.dvdishka.backuper;

import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BackupDeleteTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Assertions;

public class TestUtils {

    private static final int BUFFER_SIZE = 4096;

    public static void generateRandomFile(File targetFile, long targetByteSize) throws IOException {
        if (!targetFile.exists()) targetFile.createNewFile();
        targetFile.deleteOnExit(); // File should be deleted after test

        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            Random random = new Random();
            byte[] buffer = new byte[BUFFER_SIZE];
            long bytesWritten = 0;

            while (bytesWritten < targetByteSize) {
                random.nextBytes(buffer);
                int bytesToWrite = (int) Math.min(buffer.length, targetByteSize - bytesWritten);
                fos.write(buffer, 0, bytesToWrite);
                bytesWritten += bytesToWrite;
            }
        }
        Assertions.assertEquals(targetByteSize, targetFile.length());
    }

    public static void deleteAllBackups(Storage storage) {
        for (Backup backup : storage.getBackupManager().getBackupList()) {
            Task deleteTask = new BackupDeleteTask(backup);
            Backuper.getInstance().getTaskManager().startTask(deleteTask,
                    Backuper.getInstance().getServer().getConsoleSender(),
                    List.of(Permission.DELETE.getPermission(storage)));
        }
    }
}

