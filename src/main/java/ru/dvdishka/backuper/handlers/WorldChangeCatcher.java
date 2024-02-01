package ru.dvdishka.backuper.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.ServerCommandEvent;
import ru.dvdishka.backuper.back.Config;

public class WorldChangeCatcher implements Listener {

    @EventHandler
    public static void onPlayerInteract(PlayerInteractEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onInventoryInteract(InventoryInteractEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onBlockPlace(BlockPlaceEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onBlockBreak(BlockBreakEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onServer(ServerCommandEvent event) {
        Config.getInstance().updateLastChange();
    }
}
