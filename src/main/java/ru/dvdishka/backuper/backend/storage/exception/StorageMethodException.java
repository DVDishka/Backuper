package ru.dvdishka.backuper.backend.storage.exception;

import ru.dvdishka.backuper.backend.storage.Storage;

public class StorageMethodException extends RuntimeException {
    public StorageMethodException(Storage storage, String message) {
        super("Storage: %s. %s".formatted(storage.getId(), message));
    }

    public StorageMethodException(Storage storage, String message, Exception e) {
        super("Storage: %s. %s\n%s".formatted(storage.getId(), message, e.getMessage()), e);
        this.setStackTrace(e.getStackTrace());
    }
}
