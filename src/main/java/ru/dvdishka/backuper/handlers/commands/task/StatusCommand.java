package ru.dvdishka.backuper.handlers.commands.task;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permission;

public class StatusCommand extends Command {

    public StatusCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public boolean check() {
        if (!Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("No task is currently running");
            return false;
        }
        if (!sender.hasPermission(Permission.STATUS.getPermission())) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        long progress = Backuper.getInstance().getTaskManager().getCurrentTask().getTaskPercentProgress();
        TextColor color;
        if (!Backuper.getInstance().getTaskManager().getCurrentTask().isTaskPrepared()) {
            color = TextColor.color(190, 20, 255);
        } else if (progress < 40) {
            color = TextColor.color(190, 0, 27);
        } else if (progress < 75) {
            color = TextColor.color(190, 151, 0);
        } else {
            color = TextColor.color(0, 156, 61);
        }
        Component message = Component.empty();
        message = message
                .append(Component.text("Current task:"))
                .append(Component.space())
                .append(Component.text(Backuper.getInstance().getTaskManager().getCurrentTask().getTaskName())
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.fromHexString("#129c9b")))
                .append(Component.newline())
                .append(Component.text("Task progress:"))
                .append(Component.space())
                .append(Component.text(!Backuper.getInstance().getTaskManager().getCurrentTask().isTaskPrepared() ? "Preparing..." : "%s%%".formatted(progress))
                        .decorate(TextDecoration.BOLD)
                        .color(color));

        if (!(sender instanceof ConsoleCommandSender)) {
            message = message
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("[STATUS]")
                            .clickEvent(ClickEvent.runCommand("/backuper task status"))
                            .color(TextColor.color(17, 102, 212))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.space())
                    .append(Component.text("[CANCEL]")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xB02100))
                            .clickEvent(ClickEvent.runCommand("/backuper task cancelConfirmation")));
        }

        if (!(sender instanceof ConsoleCommandSender)) {
            sendFramedMessage(message, 15);
        } else {
            sendFramedMessage(message);
        }
    }
}
