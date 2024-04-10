package ru.dvdishka.backuper.handlers.commands;

public enum Permissions {

    BACKUPER("backuper"),

    BACKUP("backuper.backup"),
    STOP("backuper.backup.stop"),
    RESTART("backuper.backup.restart"),

    TO_ZIP("backuper.list.tozip"),
    UNZIP("backuper.list.unzip"),
    LIST("backuper.list"),
    DELETE("backuper.list.delete"),

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
