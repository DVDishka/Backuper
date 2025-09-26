package ru.dvdishka.backuper.handlers.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.CopyToTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.handlers.commands.ConfirmableCommand;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.util.List;

public class CopyToCommand extends ConfirmableCommand {

    private Storage sourceStorage;
    private Backup backup;
    private Storage targetStorage;

    public CopyToCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public boolean check() {

        sourceStorage = Backuper.getInstance().getStorageManager().getStorage((String) arguments.get("storage"));
        if (sourceStorage == null) {
            returnFailure("Wrong storage name %s".formatted((String) arguments.get("storage")));
            return false;
        }
        if (!sourceStorage.checkConnection()) {
            returnFailure("Failed to establish connection to storage %s".formatted(sourceStorage.getId()));
            return false;
        }
        backup = sourceStorage.getBackupManager().getBackup((String) arguments.get("backupName"));
        if (backup == null) {
            returnFailure("Wrong backup name %s".formatted((String) arguments.get("backupName")));
            return false;
        }
        targetStorage = Backuper.getInstance().getStorageManager().getStorage((String) arguments.get("targetStorage"));
        if (targetStorage == null) {
            returnFailure("Wrong target storage");
            return false;
        }
        if (!sourceStorage.checkConnection()) {
            returnFailure("Failed to establish connection to storage %s".formatted(targetStorage.getId()));
            return false;
        }
        if (Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("Blocked by another operation!");
            return false;
        }
        if (targetStorage.getBackupManager().getBackupList().stream().anyMatch(backup -> backup.getName().equals((String) arguments.get("backupName")))) {
            returnFailure("Target storage already contains this backup");
            return false;
        }
        if (!sender.hasPermission(Permission.STORAGE.getPermission(sourceStorage)) || !sender.hasPermission(Permission.STORAGE.getPermission(targetStorage))) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }

        setMessage(backup);
        setMainCommand("/backuper menu %s \"%s\" copyTo %s".formatted(sourceStorage.getId(), backup.getName(), targetStorage.getId()));
        return true;
    }

    @Override
    public void run() {
        Task task = new CopyToTask(backup, targetStorage);
        Backuper.getInstance().getTaskManager().startTask(task, sender, List.of(Permission.STORAGE.getPermission(sourceStorage), Permission.STORAGE.getPermission(targetStorage)));
    }
}
