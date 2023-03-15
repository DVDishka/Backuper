package ru.dvdishka.backuper.common;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface CommandInterface {

    void execute(CommandSender sender, Object[] args);

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
