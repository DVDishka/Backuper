package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.storage.FtpStorage;

public class FtpBackup implements Backup {

    private final String backupName;
    private final FtpStorage storage;

    FtpBackup(FtpStorage storage, String backupName) {
        this.backupName = backupName;
        this.storage = storage;
    }

    @Override
    public FtpStorage getStorage() {
        return storage;
    }

    @Override
    public String getName() {
        return backupName;
    }
}
