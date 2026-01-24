package ru.dvdishka.backuper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class FtpBackupIT extends BaseBackupIT {

    @BeforeEach
    public void setUp() throws IOException {
        config.set("storages.ftp.enabled", true);
        config.set("storages.ftp.type", "ftp");
        config.set("storages.ftp.backupsFolder", System.getenv("storages.ftp.backupsFolder"));

        config.set("storages.ftp.auth.address", System.getenv("storages.ftp.auth.address"));
        config.set("storages.ftp.auth.port", Integer.valueOf(System.getenv("storages.ftp.auth.port")));
        config.set("storages.ftp.auth.username", System.getenv("storages.ftp.auth.username"));
        config.set("storages.ftp.auth.password", System.getenv("storages.ftp.auth.password"));

        reload();

        storage = Backuper.getInstance().getStorageManager().getStorage("ftp"); // This storage will be no more valid after any reload so we should update this field after any reload
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
