package ru.dvdishka.backuper.handlers.commands;

public enum Permissions {

    BACKUPER("backuper"),

    BACKUP("backuper.backup"),
    STOP("backuper.backup.stop"),
    RESTART("backuper.backup.restart"),

    LOCAL_TO_ZIP("backuper.local.list.tozip"),
    LOCAL_UNZIP("backuper.local.list.unzip"),
    LOCAL_LIST("backuper.local.list"),
    LOCAL_DELETE("backuper.local.list.delete"),
    LOCAL_COPY_TO_SFTP("backuper.local.list.copytosftp"),
    LOCAL_COPY_TO_FTP("backuper.local.list.copytoftp"),

    SFTP_LIST("backuper.sftp.list"),
    SFTP_DELETE("backuper.sftp.list.delete"),
    SFTP_COPY_TO_LOCAL("backuper.sftp.list.copytolocal"),

    FTP_LIST("backuper.ftp.list"),
    FTP_DELETE("backuper.ftp.list.delete"),
    FTP_COPY_TO_LOCAL("backuper.ftp.list.copytolocal"),

    CONFIG("backuper.config"),
    RELOAD("backuper.config.reload"),

    ALERT("backuper.backup_alert"),

    STATUS("backuper.status");

    private final String permission;

    Permissions(String permission) {

        this.permission = permission;
    }

    public String getPermission() {

        return this.permission;
    }
}
