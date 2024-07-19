package ru.dvdishka.backuper.backend.common;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.util.concurrent.TimeUnit;

public class Scheduler {

    public static Scheduler getScheduler() {
        return new Scheduler();
    }

    public ScheduledTask runSync(Plugin plugin, Runnable task) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().run(plugin, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
        return null;
    }

    public ScheduledTask runSyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
        return null;
    }

    public ScheduledTask runSyncRepeatingTask(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (Utils.isFolia) {
            return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (scheduledTask) -> task.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delayTicks, periodTicks);
        }
        return null;
    }

    public ScheduledTask runAsync(Plugin plugin, Runnable task) {
        if (Utils.isFolia) {
            return Bukkit.getAsyncScheduler().runNow(plugin, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
        return null;
    }

    @SuppressWarnings("unused")
    public ScheduledTask runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (Utils.isFolia) {
            return Bukkit.getAsyncScheduler().runDelayed(plugin, (scheduledTask) -> task.run(), delayTicks * 20, TimeUnit.SECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
        return null;
    }

    public static void cancelTasks(Plugin plugin) {
        if (Utils.isFolia) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}
