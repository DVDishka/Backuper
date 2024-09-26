package ru.dvdishka.backuper.handlers.commands.task.cancel;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;

import static ru.dvdishka.backuper.handlers.commands.task.cancel.CancelConfirmationCommand.checkPermission;

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

        if (!checkPermission(sender)) {
            cancelSound();
            returnFailure("You do not have permission to cancel this task!");
            return;
        }

        buttonSound();
        sendMessage("Cancelling " + Backuper.getCurrentTask().getTaskName() + " task...");

        Backuper.getCurrentTask().cancel();
    }
}
