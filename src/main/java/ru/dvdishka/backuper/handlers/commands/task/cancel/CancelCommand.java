package ru.dvdishka.backuper.handlers.commands.task.cancel;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;

public class CancelCommand extends Command {

    public CancelCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {
        buttonSound();
        Backuper.getInstance().getTaskManager().cancelTask(Backuper.getInstance().getTaskManager().getCurrentTask(), sender);
    }
}
