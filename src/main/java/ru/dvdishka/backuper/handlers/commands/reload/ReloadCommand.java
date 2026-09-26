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
        if (!sender.hasPermission(Permission.CONFIG_RELOAD.getPermission())) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }
        if (Backuper.getInstance().getTaskManager().isLocked() || Backuper.restarting) {
            returnFailure("Blocked by another operation!");
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        Backuper plugin = Backuper.getInstance();
        if (!plugin.getTaskManager().tryLockForReload()) {
            returnFailure("Blocked by another operation!");
            return;
        }
        Backuper.restarting = true;
        try {
            if (!plugin.shutdown()) {
                returnFailure("Reload aborted: previous tasks have not stopped. See the server log.");
                return;
            }
            plugin.init();
            returnSuccess("Reloading completed");
        } catch (Exception e) {
            plugin.getLogManager().warn(e);
            returnFailure("Reload failed; see the server log. Restart the server before using Backuper again.");
        } finally {
            Backuper.restarting = false;
        }
    }
}
