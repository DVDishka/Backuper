package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.storage.SftpStorage;

public class SftpBackup implements Backup {

    private final String backupName;
    private final SftpStorage storage;

    SftpBackup(SftpStorage storage, String backupName) {
        this.storage = storage;
        this.backupName = backupName;
    }

    @Override
    public String getName() {
        return backupName;
    }

    @Override
    public SftpStorage getStorage() {
        return storage;
    }
}
