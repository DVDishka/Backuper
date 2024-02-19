package ru.dvdishka.backuper.handlers;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.*;
import ru.dvdishka.backuper.back.config.Config;

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
    public static void onItemPickUp(InventoryPickupItemEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onItemPickUp(PlayerPickItemEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onItemDrop(PlayerDropItemEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerDeath(PlayerDeathEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerQuit(PlayerQuitEvent event) {
        Config.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        Config.getInstance().updateLastChange();
    }
}
