package ru.dvdishka.backuper.handlers.worldchangecatch;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.*;
import ru.dvdishka.backuper.Backuper;

public class WorldChangeCatcher implements Listener {

    @EventHandler
    public static void onPlayerInteract(PlayerInteractEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onInventoryInteract(InventoryInteractEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onBlockPlace(BlockPlaceEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onBlockBreak(BlockBreakEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onItemDrop(PlayerDropItemEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onPlayerDeath(PlayerDeathEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onPlayerQuit(PlayerQuitEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        Backuper.getInstance().getConfigManager().updateLastChange();
    }
}
