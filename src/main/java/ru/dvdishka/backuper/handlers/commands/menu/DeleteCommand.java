package ru.dvdishka.backuper.handlers.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.handlers.commands.ConfirmableCommand;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.util.List;

public class DeleteCommand extends ConfirmableCommand {

    private Storage storage;
    private Backup backup;

    public DeleteCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public boolean check() {
        storage = Backuper.getInstance().getStorageManager().getStorage((String) arguments.get("storage"));
        if (storage == null) {
            returnFailure("Wrong storage name %s".formatted((String) arguments.get("storage")));
            return false;
        }
        if (!storage.checkConnection()) {
            returnFailure("Failed to establish connection to storage %s".formatted(storage.getId()));
            return false;
        }
        backup = storage.getBackupManager().getBackup((String) arguments.get("backupName"));
        if (backup == null) {
            returnFailure("Wrong backup name %s".formatted((String) arguments.get("backupName")));
            return false;
        }
        if (Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("Blocked by another operation!");
            return false;
        }
        if (!sender.hasPermission(Permission.DELETE.getPermission(storage))) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }

        setMessage(backup);
        return true;
    }

    @Override
    public void run() {
        Task task = backup.getDeleteTask();
        Backuper.getInstance().getTaskManager().startTaskAsync(task, sender, List.of(Permission.DELETE.getPermission(storage)));
    }
}
