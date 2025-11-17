package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.backup.Backup;

public abstract class ConfirmableCommand extends Command {

    protected Component message = null;

    protected ConfirmableCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    public void runConfirm() {
        Component header = Component.empty();
        header = header
                .append(Component.text("Confirm %s".formatted(this.getClass().getSimpleName().replace("Command", "")))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));
        Component message = net.kyori.adventure.text.Component.empty();
        if (this.message != null) {
            message = message
                    .append(this.message)
                    .append(Component.newline())
                    .append(Component.newline());
        }
        message = message
                .append(Component.text("[CONFIRM]")
                        .clickEvent(ClickEvent.callback(
                                (audience) -> this.execute(),
                                ClickCallback.Options.builder().uses(1).build()))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }

    public void executeConfirm() {
        if (!check()) {
            cancelSound();
            return;
        }
        buttonSound();
        runConfirm();
    }

    protected void setMessage(Component message) {
        this.message = message;
    }

    protected void setMessage(Backup backup) {
        this.message = Component.text(backup.getFormattedName())
                .hoverEvent(HoverEvent.showText(Component.text("(%s) (%s) %s MB".formatted(backup.getStorage().getId(), backup.getFileType().name(), backup.getMbSize()))));
    }
}
