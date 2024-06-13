package ru.dvdishka.backuper.backend.config;

public class FtpConfig {

    boolean enabled = false;
    String backupsFolder = "";
    String username = "";
    String address = "";
    String password = "";
    String pathSeparatorSymbol = "/";
    int backupsNumber = 0;
    long backupsWeight = 0;
    int port = 22;
    boolean zipArchive = true;
    int zipCompressionLevel = 5;

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

    public int getBackupsCompressionLevel() {return zipCompressionLevel;}
}
