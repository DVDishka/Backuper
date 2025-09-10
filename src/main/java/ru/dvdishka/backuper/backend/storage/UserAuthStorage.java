package ru.dvdishka.backuper.backend.storage;

import org.bukkit.command.CommandSender;

public interface UserAuthStorage extends Storage {

    void authorizeForced(CommandSender sender) throws StorageConnectionException;
}
