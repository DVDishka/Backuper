package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.tasks.Task;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public interface Backup {

    public void delete(boolean setLocked, CommandSender sender);

    public Task getDeleteTask(boolean setLocked, CommandSender sender);

    public LocalDateTime getLocalDateTime();

    public String getName();

    public default String getFormattedName() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        return getLocalDateTime().format(formatter);
    }

    public long getByteSize(CommandSender sender);

    public long getMbSize(CommandSender sender);

    public String getFileType();
}
