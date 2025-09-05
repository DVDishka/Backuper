package ru.dvdishka.backuper.handlers.worldchangecatch;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.dvdishka.backuper.backend.config.ConfigManager;

import java.util.List;

public class WorldChangeCatcherNew implements Listener {

    public static List<String> eventNames = List.of("io.papermc.paper.event.player.PlayerInventorySlotChangeEvent", "io.papermc.paper.event.player.PlayerPickItemEvent");

    @EventHandler
    public static void onPlayerInventoryEvent(PlayerInventorySlotChangeEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onItemPickUp(PlayerPickItemEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }
}
