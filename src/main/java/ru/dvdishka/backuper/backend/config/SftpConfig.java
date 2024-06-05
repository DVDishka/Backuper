package ru.dvdishka.backuper.backend.config;

public class SftpConfig {

    boolean enabled = false;
    String sshConfigFile = "";
    String backupsFolder = "";
    String authType = "password";
    String username = "";
    String address = "";
    String password = "";
    String knownHostsFilePath = "";
    String useKnownHostsFile = "false";
    String keyFilePath = "";
    String pathSeparatorSymbol = "/";
    int backupsNumber = 0;
    long backupsWeight = 0;
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

    public String getAuthType() {
        return authType;
    }

    public String getKnownHostsFilePath() {
        return knownHostsFilePath;
    }

    public String getPassword() {
        return password;
    }

    public String getUseKnownHostsFile() {
        return useKnownHostsFile;
    }

    public String getUsername() {
        return username;
    }

    public String getKeyFilePath() {
        return keyFilePath;
    }

    public String getPathSeparatorSymbol() {
        return pathSeparatorSymbol;
    }

    public int getBackupsNumber() {return backupsNumber;}

    public long getBackupsWeight() {return backupsWeight;}

    public String getSshConfigFile() {
        return sshConfigFile;
    }
}
