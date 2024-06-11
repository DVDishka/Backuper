package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.tasks.Task;

import java.time.LocalDateTime;

public class FtpBackup implements Backup {

    public void delete(boolean setLocked, CommandSender sender) {

    }

    public Task getDeleteTask(boolean setLocked, CommandSender sender) {
        return null;
    }

    public LocalDateTime getLocalDateTime() {
        return null;
    }

    public String getName() {
        return "";
    }

    public long getByteSize(CommandSender sender) {
        return 0;
    }

    public long getMbSize(CommandSender sender) {
        return 0;
    }

    public String getFileType() {
        return "";
    }
}
