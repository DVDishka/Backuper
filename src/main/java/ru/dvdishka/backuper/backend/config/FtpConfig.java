package ru.dvdishka.backuper.backend.config;

public class FtpConfig {

    boolean enabled;
    boolean autoBackup;
    String backupsFolder;
    String username;
    String address;
    String password;
    String pathSeparatorSymbol;
    int backupsNumber;
    long backupsWeight;
    int port;
    boolean zipArchive;
    int zipCompressionLevel;

    public String getBackupsFolder() {
        return backupsFolder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPort() {
        return port;
    }

    public String getAddress() {
        return address;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    public String getPathSeparatorSymbol() {
        return pathSeparatorSymbol;
    }

    public int getBackupsNumber() {return backupsNumber;}

    public long getBackupsWeight() {return backupsWeight;}

    public boolean isZipArchive() {return zipArchive;}

    public int getZipCompressionLevel() {return zipCompressionLevel;}

    public boolean isAutoBackup() {
        return autoBackup;
    }
}
