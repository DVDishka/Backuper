package ru.dvdishka.backuper.common;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public interface CommandInterface {

    void execute(CommandSender sender, CommandArguments args);

    default void returnSuccess(String message, @NotNull CommandSender sender) {

        sender.sendMessage(ChatColor.GREEN + (ChatColor.BOLD + message));
    }

    default void returnFailure(String message, @NotNull CommandSender sender) {

        sender.sendMessage(ChatColor.RED + (ChatColor.BOLD + message));
    }

    default void returnSuccess(String message, @NotNull CommandSender sender, ChatColor color) {

        sender.sendMessage(color + (ChatColor.BOLD + message));
    }

    default void returnFailure(String message, @NotNull CommandSender sender, ChatColor color) {

        sender.sendMessage(color + (ChatColor.BOLD + message));
    }
}
