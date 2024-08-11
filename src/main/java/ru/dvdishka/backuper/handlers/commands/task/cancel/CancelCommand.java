package ru.dvdishka.backuper.handlers.commands.task.cancel;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.ArrayList;

public class CancelCommand extends Command {

    public CancelCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Backuper.isLocked()) {
            cancelSound();
            returnFailure("There is no running task!");
            return;
        }

        buttonSound();
        sendMessage("Cancelling task...");

        Backuper.getCurrentTask().cancel();
    }
}
