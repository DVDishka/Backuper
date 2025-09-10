package ru.dvdishka.backuper.handlers.commands;

import ru.dvdishka.backuper.backend.storage.Storage;

public enum Permission {

    BACKUPER("backuper"),

    STOP("backuper.backup.stop"),
    RESTART("backuper.backup.restart"),
    ALERT("backuper.backup_alert"),

    STORAGE("backuper.%s"),
    BACKUP("backuper.%s.backup"),
    TO_ZIP("backuper.%s.list.tozip"),
    UNZIP("backuper.%s.list.unzip"),
    LIST("backuper.%s.list"),
    DELETE("backuper.%s.list.delete"),
    ACCOUNT("backuper.%s.account"),

    CONFIG("backuper.config"),
    CONFIG_RELOAD("backuper.config.reload"),

    STATUS("backuper.status");

    private final String permission;

    Permission(String permission) {
        this.permission = permission;
    }

    public String getPermission(Storage storage) {
        return this.permission.formatted(storage.getId());
    }

    public String getPermission() {
        return this.permission;
    }
}
