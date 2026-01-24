package ru.dvdishka.backuper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SftpBackupIT extends BaseBackupIT {

    @BeforeEach
    public void setUp() throws IOException {
        config.set("storages.sftp.enabled", true);
        config.set("storages.sftp.type", "sftp");
        config.set("storages.sftp.backupsFolder", System.getenv("storages.sftp.backupsFolder"));

        config.set("storages.sftp.auth.address", System.getenv("storages.sftp.auth.address"));
        config.set("storages.sftp.auth.port", Integer.valueOf(System.getenv("storages.sftp.auth.port")));
        config.set("storages.sftp.auth.authType", System.getenv("storages.sftp.auth.authType"));
        config.set("storages.sftp.auth.username", System.getenv("storages.sftp.auth.username"));
        config.set("storages.sftp.auth.password", System.getenv("storages.sftp.auth.password"));
        config.set("storages.sftp.auth.keyFilePath", System.getenv("storages.sftp.auth.keyFilePath"));
        config.set("storages.sftp.auth.useKnownHostsFile", Boolean.parseBoolean(System.getenv("storages.sftp.auth.useKnownHostsFile")));
        config.set("storages.sftp.auth.knownHostsFilePath", System.getenv("storages.sftp.auth.knownHostsFilePath"));
        config.set("storages.sftp.auth.sshConfigFilePath", System.getenv("storages.sftp.auth.sshConfigFilePath"));

        reload();

        storage = Backuper.getInstance().getStorageManager().getStorage("sftp"); // This storage will be no more valid after any reload so we should update this field after any reload
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
