package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.storage.S3Storage;

public class S3Backup implements Backup {

    private final String backupName;
    private final S3Storage storage;

    S3Backup(S3Storage storage, String backupName) {
        this.backupName = backupName;
        this.storage = storage;
    }

    @Override
    public S3Storage getStorage() {
        return storage;
    }

    @Override
    public String getName() {
        return backupName;
    }
}
