package ru.dvdishka.backuper.common;

public enum Permissions {

    BACKUP("backuper.backup"),
    STOP("backuper.stop"),
    RESTART("backuper.restart");

    private final String permission;

    Permissions(String permission) {

        this.permission = permission;
    }

    public String getPermission() {

        return this.permission;
    }
}
