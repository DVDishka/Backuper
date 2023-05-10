package ru.dvdishka.backuper.common;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public interface CommandInterface {

    default void returnSuccess(String message, CommandSender sender) {

        sender.sendMessage(ChatColor.GREEN + (ChatColor.BOLD + message));
    }

    default void returnFailure(String message, CommandSender sender) {

        sender.sendMessage(ChatColor.RED + (ChatColor.BOLD + message));
    }

    default void returnSuccess(String message, CommandSender sender, ChatColor color) {

        sender.sendMessage(color + (ChatColor.BOLD + message));
    }

    default void returnFailure(String message, CommandSender sender, ChatColor color) {

        sender.sendMessage(color + (ChatColor.BOLD + message));
    }
}
