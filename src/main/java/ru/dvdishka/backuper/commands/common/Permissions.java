package ru.dvdishka.backuper.commands.common;

public enum Permissions {

    BACKUP("backuper.backup"),
    STOP("backuper.stop"),
    RESTART("backuper.restart"),
    LIST("backuper.list"),
    RELOAD("backuper.reload"),
    DELETE("backuper.delete"),
    MAKE_ZIP("backuper.makeZip"),
    UNZIP("backuper.unZip");

    private final String permission;

    Permissions(String permission) {

        this.permission = permission;
    }

    public String getPermission() {

        return this.permission;
    }
}
