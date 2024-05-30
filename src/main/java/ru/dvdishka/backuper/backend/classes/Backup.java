package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.tasks.Task;

import java.time.LocalDateTime;

public interface Backup {

    public void delete(boolean setLocked, CommandSender sender);

    public Task getDeleteTask(boolean setLocked, CommandSender sender);

    public LocalDateTime getLocalDateTime();

    public String getName();

    public long getByteSize(CommandSender sender);

    public long getMbSize(CommandSender sender);

    public String getFileType();
}
