package ru.dvdishka.backuper.backend.exception;

import ru.dvdishka.backuper.backend.backup.Backup;

public class StorageQuotaExceededException extends Exception {

    private final Backup.StorageType storageType;

    public StorageQuotaExceededException(Backup.StorageType storageType) {
        super("%s storage quota limit reached, try again later".formatted(storageType.name()));
        this.storageType = storageType;
    }

    public Backup.StorageType getStorageType() {
        return storageType;
    }
}
