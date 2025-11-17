package ru.dvdishka.backuper.backend.config;

public interface StorageConfig extends Config {

    String getId();// Required to implement the getBackupsFolder method in GoogleDrive storage

    boolean isEnabled();

    boolean isAutoBackup();

    int getZipCompressionLevel();

    boolean isZipArchive();

    String getBackupsFolder();

    int getBackupsNumber();

    long getBackupsWeight();
}
