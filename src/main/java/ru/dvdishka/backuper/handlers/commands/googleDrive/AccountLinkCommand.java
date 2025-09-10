package ru.dvdishka.backuper.handlers.commands.googleDrive;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.UserAuthStorage;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permission;

public class AccountLinkCommand extends Command {

    private UserAuthStorage storage;

    public AccountLinkCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public boolean check() {
        if (Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("You cannot link your account while some process is running");
            return false;
        }
        Storage storage = Backuper.getInstance().getStorageManager().getStorage((String) arguments.get("storage"));
        if (storage == null) {
            returnFailure("Wrong storage name %s".formatted((String) arguments.get("storage")));
            return false;
        }
        if (!sender.hasPermission(Permission.ACCOUNT.getPermission(storage))) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }
        if (!(storage instanceof UserAuthStorage)) {
            returnFailure("There is no option to link account to this storage %s".formatted((String) arguments.get("storage")));
            return false;
        }
        this.storage = (UserAuthStorage) storage;

        return true;
    }

    @Override
    public void run() {
        try {
            storage.authorizeForced(sender);
        } catch (Storage.StorageConnectionException e) {
            Backuper.getInstance().getLogManager().warn("Failed to link account to %s storage".formatted(storage.getId()), sender);
            Backuper.getInstance().getLogManager().warn(e);
        }
    }
}
