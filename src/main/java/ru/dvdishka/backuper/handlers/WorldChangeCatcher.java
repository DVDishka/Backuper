package ru.dvdishka.backuper.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.dvdishka.backuper.back.Config;

public class WorldChangeCatcher implements Listener {

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        Config.getInstance().updateLastChange();
    }
}
