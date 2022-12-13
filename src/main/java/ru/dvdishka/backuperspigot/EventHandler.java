package ru.dvdishka.backuperspigot;

import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class EventHandler implements Listener {

    @org.bukkit.event.EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        if (CommonVariables.isBackuping) {

            event.getPlayer().kickPlayer("Server goes to backup!");
        }
    }
}
