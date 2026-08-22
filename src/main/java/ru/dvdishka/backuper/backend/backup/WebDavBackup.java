package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.storage.WebDavStorage;

public class WebDavBackup implements Backup {

    private final String backupName;
    private final WebDavStorage storage;

    WebDavBackup(WebDavStorage storage, String backupName) {
        this.backupName = backupName;
        this.storage = storage;
    }

    @Override
    public WebDavStorage getStorage() {
        return storage;
    }

    @Override
    public String getName() {
        return backupName;
    }
}
