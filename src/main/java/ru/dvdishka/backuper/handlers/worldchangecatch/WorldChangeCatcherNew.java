package ru.dvdishka.backuper.handlers.worldchangecatch;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.dvdishka.backuper.Backuper;

import java.util.List;

public class WorldChangeCatcherNew implements Listener {

    public static List<String> eventNames = List.of("io.papermc.paper.event.player.PlayerInventorySlotChangeEvent", "io.papermc.paper.event.player.PlayerPickItemEvent");

    @EventHandler
    public static void onPlayerInventoryEvent(PlayerInventorySlotChangeEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onItemPickUp(PlayerPickItemEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }
}
