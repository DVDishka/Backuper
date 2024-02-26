package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.Common;

public abstract class Command {

    protected CommandSender sender;
    protected CommandArguments arguments;

    protected Command(CommandSender sender, CommandArguments arguments) {
        this.sender = sender;
        this.arguments = arguments;
    }

    protected abstract void execute();

    protected void returnSuccess(String message) {
        Common.returnSuccess(message, sender);
    }

    @SuppressWarnings("unused")
    protected void returnSuccess(String message, TextColor color) {
        Common.returnSuccess(message, sender, color);
    }

    protected void returnFailure(String message) {
        Common.returnFailure(message, sender);
    }

    @SuppressWarnings("unused")
    protected void returnFailure(String message, TextColor color) {
        Common.returnFailure(message, sender, color);
    }

    protected void returnWarning(String message, TextColor color) {
        Common.returnWarning(message, sender, color);
    }

    protected void returnWarning(String message) {
        Common.returnWarning(message, sender);
    }

    protected void sendMessage(String message) {
        Common.sendMessage(message, sender);
    }

    protected void cancelButtonSound() {
        Common.cancelButtonSound(sender);
    }

    protected void normalButtonSound() {
        Common.normalButtonSound(sender);
    }
}
