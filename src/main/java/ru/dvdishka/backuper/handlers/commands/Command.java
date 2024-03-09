package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.Utils;

public abstract class Command {

    protected CommandSender sender;
    protected CommandArguments arguments;

    protected Command(CommandSender sender, CommandArguments arguments) {
        this.sender = sender;
        this.arguments = arguments;
    }

    protected abstract void execute();

    protected void returnSuccess(String message) {
        Utils.returnSuccess(message, sender);
    }

    @SuppressWarnings("unused")
    protected void returnSuccess(String message, TextColor color) {
        Utils.returnSuccess(message, sender, color);
    }

    protected void returnFailure(String message) {
        Utils.returnFailure(message, sender);
    }

    @SuppressWarnings("unused")
    protected void returnFailure(String message, TextColor color) {
        Utils.returnFailure(message, sender, color);
    }

    protected void returnWarning(String message, TextColor color) {
        Utils.returnWarning(message, sender, color);
    }

    protected void returnWarning(String message) {
        Utils.returnWarning(message, sender);
    }

    protected void sendMessage(String message) {
        Utils.sendMessage(message, sender);
    }

    protected void cancelButtonSound() {
        Utils.cancelButtonSound(sender);
    }

    protected void normalButtonSound() {
        Utils.normalButtonSound(sender);
    }
}
