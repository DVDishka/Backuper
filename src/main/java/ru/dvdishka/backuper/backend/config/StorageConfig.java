package ru.dvdishka.backuper.backend.config;

public interface StorageConfig {

    int getZipCompressionLevel();

    boolean isZipArchive();

    String getBackupsFolder();
}
