package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.storage.LocalStorage;

import java.io.File;

public class LocalBackup implements Backup {

    private final String backupName;
    private final LocalStorage storage;

    LocalBackup(LocalStorage storage, String backupName) {
        this.backupName = backupName;
        this.storage = storage;
    }

    @Override
    public String getName() {
        return backupName;
    }

    public File getFile() {
        File backupsFolder = new File(storage.getConfig().getBackupsFolder());

        if (BackupFileType.ZIP.equals(this.getFileType())) {
            return backupsFolder.toPath().resolve("%s.zip".formatted(backupName)).toFile();
        } else {
            return backupsFolder.toPath().resolve(backupName).toFile();
        }
    }

    @Override
    public LocalStorage getStorage() {
        return storage;
    }
}
