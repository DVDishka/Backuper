package ru.dvdishka.backuper.handlers.commands.reload;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permission;

public class ReloadCommand extends Command {

    public ReloadCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public boolean check() {
        if (sender.hasPermission(Permission.CONFIG_RELOAD.getPermission())) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }
        if (Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("Blocked by another operation!");
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        Backuper.getInstance().shutdown();
        Backuper.getInstance().init();
        returnSuccess("Reloading completed");
    }
}
