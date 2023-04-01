package ru.dvdishka.backuper.tasks;

import org.bukkit.Bukkit;
import ru.dvdishka.backuper.common.CommonVariables;

public class RestartSafelyTask implements Runnable {

    @Override
    public void run() {

        Bukkit.getAsyncScheduler().cancelTasks(CommonVariables.plugin);
        Bukkit.getServer().spigot().restart();
    }
}
