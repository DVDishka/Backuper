package ru.dvdishka.backuper.handlers.commands.task.cancel;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

public class CancelConfirmationCommand extends Command {

    public CancelConfirmationCommand(CommandSender sender, CommandArguments arguments) {
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

        Component header = Component.empty();

        header = header
                .append(Component.text("Confirm Task Cancelling")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        long progress = Backuper.getCurrentTask().getTaskPercentProgress();
        TextColor color;

        if (progress < 40) {
            color = TextColor.color(190, 0, 27);
        } else if (progress < 75) {
            color = TextColor.color(190, 151, 0);
        } else {
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
                .append(Component.newline())
                .append(Component.text("[CANCEL]")
                        .clickEvent(ClickEvent.runCommand("/backuper task cancel"))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }

    public static boolean checkPermission(CommandSender sender) {

        for (Permissions permission : Backuper.getCurrentTask().getPermissions()) {
            if (!sender.hasPermission(permission.getPermission())) {
                return false;
            }
        }
        return true;
    }
}
