package ru.dvdishka.backuper.backend.config;

public class LocalConfig {

    boolean enabled;
    boolean autoBackup;
    String backupsFolder;
    int backupsNumber;
    long backupsWeight;
    boolean zipArchive;
    int zipCompressionLevel;

    public int getBackupsNumber() {
        return backupsNumber;
    }

    public long getBackupsWeight() {
        return backupsWeight;
    }

    public boolean isZipArchive() {
        return zipArchive;
    }

    public String getBackupsFolder() {
        return backupsFolder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getZipCompressionLevel() {
        return zipCompressionLevel;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }
}
