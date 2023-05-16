package ru.dvdishka.backuper.common;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public interface CommandInterface {

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
