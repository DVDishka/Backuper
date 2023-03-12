package ru.dvdishka.backuper.common;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public interface CommandInterface {

    void execute(Player sender, Object[] args);

    default void returnSuccess(String message, Player player) {

        player.sendMessage(ChatColor.GREEN + (ChatColor.BOLD + message));
    }

    default void returnFailure(String message, Player player) {

        player.sendMessage(ChatColor.RED + (ChatColor.BOLD + message));
    }

    default void returnSuccess(String message, Player player, ChatColor color) {

        player.sendMessage(color + (ChatColor.BOLD + message));
    }

    default void returnFailure(String message, Player player, ChatColor color) {

        player.sendMessage(color + (ChatColor.BOLD + message));
    }
}
