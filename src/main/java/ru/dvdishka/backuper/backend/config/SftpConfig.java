package ru.dvdishka.backuper.backend.config;

public class SftpConfig implements StorageConfig {

    boolean enabled;
    boolean autoBackup;
    String sshConfigFile;
    String backupsFolder;
    String authType;
    String username;
    String address;
    String password;
    String knownHostsFilePath;
    String useKnownHostsFile;
    String keyFilePath;
    String pathSeparatorSymbol;
    int backupsNumber;
    long backupsWeight;
    boolean zipArchive;
    int zipCompressionLevel;
    int port;

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

    public int getBackupsNumber() {
        return backupsNumber;
    }

    public long getBackupsWeight() {
        return backupsWeight;
    }

    public String getSshConfigFile() {
        return sshConfigFile;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public boolean isZipArchive() {
        return zipArchive;
    }

    public int getZipCompressionLevel() {
        return zipCompressionLevel;
    }
}
