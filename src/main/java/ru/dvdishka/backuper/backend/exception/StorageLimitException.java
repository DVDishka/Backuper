package ru.dvdishka.backuper.backend.exception;

import ru.dvdishka.backuper.backend.backup.Backup;

public class StorageLimitException extends Exception {
    private final Backup.StorageType storageType;

    public StorageLimitException(Backup.StorageType storageType) {
        super("%s storage space limit reached".formatted(storageType.name()));
        this.storageType = storageType;
    }

    public Backup.StorageType getStorageType() {
        return storageType;
    }}
