package ru.dvdishka.backuper.backend.backup;

public enum StorageType {

    LOCAL(),
    FTP(),
    SFTP(),
    GOOGLE_DRIVE(),
    NULL();
}
