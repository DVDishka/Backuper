package ru.dvdishka.backuper.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.dvdishka.backuper.backend.util.AdminInfoUtils;

public class AdminJoinHandler implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        AdminInfoUtils.sendPluginVersionCheck(event.getPlayer());
    }
}
