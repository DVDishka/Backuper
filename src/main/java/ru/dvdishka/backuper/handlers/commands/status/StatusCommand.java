package ru.dvdishka.backuper.handlers.commands.status;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

public class StatusCommand extends Command {

    public StatusCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backup.getCurrentTask() == null) {
            cancelButtonSound();
            sendMessage("No tasks are currently running");
            return;
        }

        normalButtonSound();

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
                .append(Component.text(Backup.getCurrentTask().getTaskName())
                        .decorate(TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Task progress:"))
                .append(Component.space())
                .append(Component.text(progress + "%")
                        .decorate(TextDecoration.BOLD)
                        .color(color));

        sendFramedMessage(message);
    }

    public static void sendTaskStartedMessage(String taskName, CommandSender sender) {

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender) && sender.hasPermission(Permissions.STATUS.getPermission())) {

            message = message
                    .append(Component.text("The " + taskName + " task has been started, you can check the task status by clicking on "))
                    .append(Component.text("[STATUS]")
                            .color(TextColor.color(17, 102, 212)))
                    .append(Component.text(" button below or using command "))
                    .append(Component.text("/backup status")
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/backup status")))
                    .append(Component.newline());

            message = message
                    .append(Component.newline())
                    .append(Component.text("[STATUS]")
                            .clickEvent(ClickEvent.runCommand("/backup status"))
                    .color(TextColor.color(17, 102, 212))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.newline());
        }
        else if (sender instanceof ConsoleCommandSender) {

            message = message
                    .append(Component.text("The " + taskName + " task has been started, you can check the task status using command "))
                    .append(Component.text("/backup status")
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/backup status")));
        }

        Utils.sendFramedMessage(message, sender);
    }
}
