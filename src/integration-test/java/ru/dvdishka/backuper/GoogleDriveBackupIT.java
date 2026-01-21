package ru.dvdishka.backuper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class GoogleDriveBackupIT extends BaseBackupIT {

    @BeforeEach
    public void setUp() throws IOException {
        config.set("storages.googleDrive.enabled", true);
        config.set("storages.googleDrive.type", "googleDrive");

        config.set("storages.googleDrive.backupsFolderId", System.getenv("storages.googleDrive.backupsFolderId"));
        config.set("storages.googleDrive.createBackuperFolder", Boolean.valueOf(System.getenv("storages.googleDrive.createBackuperFolder")));

        // Token in this file must be already authorized because there is no way to authorize via integration tests
        config.set("storages.googleDrive.auth.tokenFolderPath", System.getenv("storages.googleDrive.auth.tokenFolderPath"));

        reload();

        storage = Backuper.getInstance().getStorageManager().getStorage("googleDrive"); // This storage will be no more valid after any reload so we should update this field after any reload
    }

    @Test
    @Override
    public void smallFolderTest() throws IOException, InterruptedException {
        super.smallFolderTest();
    }

    @Test
    @Override
    public void smallZipBackupTest() throws IOException, InterruptedException {
        super.smallZipBackupTest();
    }

    @Test
    @Override
    public void bigFolderBackupTest() throws IOException, InterruptedException {
        super.bigFolderBackupTest();
    }

    @Test
    @Override
    public void bigZipBackupTest() throws IOException, InterruptedException {
        super.bigZipBackupTest();
    }
}
