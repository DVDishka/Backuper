package ru.dvdishka.backuper.backend.config;

public class LocalConfig {

    boolean enabled = true;
    String backupsFolder = "plugins/Backuper/Backups";
    int backupsNumber = 0;
    long backupsWeight = 0;
    boolean zipArchive = true;
    int zipCompressionLevel = 5;

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
}
