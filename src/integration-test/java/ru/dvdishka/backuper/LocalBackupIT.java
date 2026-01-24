package ru.dvdishka.backuper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class LocalBackupIT extends BaseBackupIT {

    @BeforeEach
    public void setUp() {
        storage = Backuper.getInstance().getStorageManager().getStorage("local"); // This storage will be no more valid after any reload so we should update this field after any reload
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