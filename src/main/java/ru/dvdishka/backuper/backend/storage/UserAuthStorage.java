package ru.dvdishka.backuper.backend.storage;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;

public interface UserAuthStorage extends Storage {

    void authorizeForced(CommandSender sender) throws StorageConnectionException;
}
