package ru.dvdishka.backuper.commands.common;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.dvdishka.backuper.common.Common;

public interface CommandInterface {

    void execute(CommandSender sender, CommandArguments args);

    default void returnSuccess(String message, @NotNull CommandSender sender) {
        Common.returnSuccess(message, sender);
    }

    default void returnFailure(String message, @NotNull CommandSender sender) {
        Common.returnFailure(message, sender);
    }

    @SuppressWarnings("unused")
    default void returnSuccess(String message, @NotNull CommandSender sender, ChatColor color) {
        Common.returnSuccess(message, sender, color);
    }

    @SuppressWarnings("unused")
    default void returnFailure(String message, @NotNull CommandSender sender, ChatColor color) {
        Common.returnFailure(message, sender, color);
    }

    default void sendMessage(String message, @NotNull CommandSender sender) {
        Common.sendMessage(message, sender);
    }

    default void cancelButtonSound(CommandSender sender) {
        Common.cancelButtonSound(sender);
    }

    default void normalButtonSound(CommandSender sender) {
        Common.normalButtonSound(sender);
    }
}
