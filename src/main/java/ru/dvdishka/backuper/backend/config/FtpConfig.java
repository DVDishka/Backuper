package ru.dvdishka.backuper.backend.config;

public class FtpConfig {

    boolean enabled = false;
    String backupsFolder = "";
    String username = "";
    String address = "";
    String password = "";
    String pathSeparatorSymbol = "/";
    int port = 22;

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
}
