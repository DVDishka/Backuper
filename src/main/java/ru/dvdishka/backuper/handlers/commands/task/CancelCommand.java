package ru.dvdishka.backuper.handlers.commands.task;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.handlers.commands.ConfirmableCommand;

public class CancelCommand extends ConfirmableCommand {

    public CancelCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public boolean check() {
        if (!Backuper.getInstance().getTaskManager().isLocked()) {
            returnFailure("No task is currently running");
            return false;
        }
        // Permission check in run()

        long progress = Backuper.getInstance().getTaskManager().getCurrentTask().getTaskPercentProgress();
        TextColor color;
        if (progress < 40) {
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
                        .color(UIUtils.getSecondaryColor()))
                .append(Component.newline())
                .append(Component.text("Task progress:"))
                .append(Component.space())
                .append(Component.text("%s%%".formatted(progress))
                        .decorate(TextDecoration.BOLD)
                        .color(color));

        setMessage(message);
        setMainCommand("/backuper task cancel");
        return true;
    }

    @Override
    public void run() {
        Backuper.getInstance().getTaskManager().cancelTask(Backuper.getInstance().getTaskManager().getCurrentTask(), sender);
    }
}
