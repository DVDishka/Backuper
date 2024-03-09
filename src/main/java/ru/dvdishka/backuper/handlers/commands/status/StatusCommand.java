package ru.dvdishka.backuper.handlers.commands.status;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.handlers.commands.Command;

public class StatusCommand extends Command {

    public StatusCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backup.getCurrentTask() == null) {
            sendMessage("No tasks are currently running");
            return;
        }

        Component message = Component.empty();

        message = message
                .append(Component.text("Current task:"))
                .append(Component.space())
                .append(Component.text(Backup.getCurrentTask().getTaskName()))
                .append(Component.newline())
                .append(Component.text("Task progress:"))
                .append(Component.space())
                .append(Component.text(Backup.getCurrentTask().getTaskProgress()))
                .append(Component.text("%"));

        sender.sendMessage(message);
    }
}
