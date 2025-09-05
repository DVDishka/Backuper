package ru.dvdishka.backuper.handlers.worldchangecatch;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.*;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.ConfigManager;

public class WorldChangeCatcher implements Listener {

    @EventHandler
    public static void onPlayerInteract(PlayerInteractEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onInventoryInteract(InventoryInteractEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onBlockPlace(BlockPlaceEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onBlockBreak(BlockBreakEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onItemDrop(PlayerDropItemEvent event) {
        Backuper.getInstance().getC.updateLastChange();
    }

    @EventHandler
    public static void onPlayerDeath(PlayerDeathEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerQuit(PlayerQuitEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        ConfigManager.getInstance().updateLastChange();
    }
}
