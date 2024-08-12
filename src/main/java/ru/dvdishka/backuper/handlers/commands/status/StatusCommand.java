package ru.dvdishka.backuper.handlers.commands.status;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.ArrayList;

public class StatusCommand extends Command {

    public StatusCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (Backuper.getCurrentTask() == null) {
            cancelSound();
            returnFailure("No tasks are currently running");
            return;
        }

        buttonSound();

        Component message = Component.empty();

        long progress = Backuper.getCurrentTask().getTaskPercentProgress();
        TextColor color;

        if (progress < 40) {
            color = TextColor.color(190, 0, 27);
        }
        else if (progress < 75) {
            color = TextColor.color(190, 151, 0);
        }
        else {
            color = TextColor.color(0, 156, 61);
        }

        message = message
                .append(Component.text("Current task:"))
                .append(Component.space())
                .append(Component.text(Backuper.getCurrentTask().getTaskName())
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.fromHexString("#129c9b")))
                .append(Component.newline())
                .append(Component.text("Task progress:"))
                .append(Component.space())
                .append(Component.text(progress + "%")
                        .decorate(TextDecoration.BOLD)
                        .color(color))
                .append(Component.newline())
                .append(Component.text("[CANCEL]")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100))
                        .clickEvent(ClickEvent.runCommand("/backuper task cancelConfirmation")));

        if (!(sender instanceof ConsoleCommandSender)) {
            sendFramedMessage(message, 15);
        }
        else {
            sendFramedMessage(message);
        }
    }

    public static void sendTaskStartedMessage(String taskName, CommandSender sender) {

        if (!sender.hasPermission(Permissions.STATUS.getPermission())) {
            return;
        }

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender)) {

            message = message
                    .append(Component.text("The "))
                    .append(Component.text(taskName)
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x4974B)))
                    .append(Component.text(" task has been started, you can check the task status by clicking on "))
                    .append(Component.text("[STATUS]")
                            .color(TextColor.color(17, 102, 212)))
                    .append(Component.text(" button below or using command "))
                    .append(Component.text("/backuper status")
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/backuper status"))
                            .hoverEvent(HoverEvent.showText(Component
                                    .text("/backuper status")
                                            .decorate(TextDecoration.ITALIC))))
                    .append(Component.newline());

            message = message
                    .append(Component.newline())
                    .append(Component.text("[STATUS]")
                            .clickEvent(ClickEvent.runCommand("/backuper status"))
                            .color(TextColor.color(17, 102, 212))
                            .decorate(TextDecoration.BOLD));
        }
        else {

            message = message
                    .append(Component.text("The "))
                    .append(Component.text(taskName)
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x4974B)))
                    .append(Component.text(" task has been started,"))
                    .append(Component.newline())
                    .append(Component.text("you can check the task status using command"))
                    .append(Component.newline())
                    .append(Component.text("/backuper status")
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/backuper status")));
        }

        UIUtils.sendFramedMessage(message, sender);
    }
}
