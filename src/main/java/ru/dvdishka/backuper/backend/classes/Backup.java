package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.backend.config.Config;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public interface Backup {

    public void delete(boolean setLocked, CommandSender sender);

    public Task getDeleteTask(boolean setLocked, CommandSender sender);

    public LocalDateTime getLocalDateTime();

    public String getName();

    public default String getFormattedName() {
        return getLocalDateTime().format(Config.getInstance().getDateTimeFormatter());
    }

    public long getByteSize(CommandSender sender);

    public long getMbSize(CommandSender sender);

    public String getFileType();

    public String getFileName();

    public String getPath();
}
