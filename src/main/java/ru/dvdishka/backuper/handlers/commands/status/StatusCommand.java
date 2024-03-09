package ru.dvdishka.backuper.handlers.commands.status;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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

        long progress = Backup.getCurrentTask().getTaskProgress();
        TextColor color;

        if (progress < 25) {
            color = TextColor.color(190, 0, 27);
        }
        else if (progress < 70) {
            color = TextColor.color(190, 151, 0);
        }
        else {
            color = TextColor.color(0, 156, 61);
        }

        message = message
                .append(Component.text("Current task:"))
                .append(Component.space())
                .append(Component.text(Backup.getCurrentTask().getTaskName()))
                .append(Component.newline())
                .append(Component.text("Task progress:"))
                .append(Component.space())
                .append(Component.text(progress + "%")
                        .color(color));

        sender.sendMessage(message);
    }
}
