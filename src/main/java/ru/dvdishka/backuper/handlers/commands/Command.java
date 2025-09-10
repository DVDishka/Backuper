package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.util.UIUtils;

public abstract class Command {

    protected CommandSender sender;
    protected CommandArguments arguments;

    protected Command(CommandSender sender, CommandArguments arguments) {
        this.sender = sender;
        this.arguments = arguments;
    }

    /***
     * All checks should be performed here. Permission checks should be performed too
     * @return Returns if all checks were performed successfully
     */
    public abstract boolean check();

    /***
     * Main command logic should be declared here
     */
    public abstract void run();

    /***
     * Performs checks and runs main logic if all checks were performed successfully
     */
    public void execute() {
        if (!check()) {
            cancelSound();
            return;
        }
        buttonSound();
        run();
    }

    protected void returnSuccess(String message) {
        UIUtils.returnSuccess(message, sender);
    }

    @SuppressWarnings("unused")
    protected void returnSuccess(String message, TextColor color) {
        UIUtils.returnSuccess(message, sender, color);
    }

    protected void returnFailure(String message) {
        UIUtils.returnFailure(message, sender);
    }

    @SuppressWarnings("unused")
    protected void returnFailure(String message, TextColor color) {
        UIUtils.returnFailure(message, sender, color);
    }

    protected void returnWarning(String message, TextColor color) {
        UIUtils.returnWarning(message, sender, color);
    }

    protected void returnWarning(String message) {
        UIUtils.returnWarning(message, sender);
    }

    protected void sendMessage(String message) {
        UIUtils.sendMessage(message, sender);
    }

    protected void cancelSound() {
        UIUtils.cancelSound(sender);
    }

    protected void buttonSound() {
        UIUtils.normalSound(sender);
    }

    protected void successSound() {
        UIUtils.successSound(sender);
    }

    protected void notificationSound() {
        UIUtils.notificationSound(sender);
    }

    protected void sendFramedMessage(Component message) {
        UIUtils.sendFramedMessage(message, sender);
    }

    protected void sendFramedMessage(Component message, int dashNumber) {
        UIUtils.sendFramedMessage(message, dashNumber, sender);
    }

    protected void sendFramedMessage(Component header, Component message) {
        UIUtils.sendFramedMessage(header, message, sender);
    }

    protected void sendFramedMessage(Component header, Component message, int dashNumber) {
        UIUtils.sendFramedMessage(header, message, dashNumber, sender);
    }
}
