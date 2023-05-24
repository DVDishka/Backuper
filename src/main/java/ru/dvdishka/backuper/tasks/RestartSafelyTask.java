package ru.dvdishka.backuper.tasks;

import org.bukkit.Bukkit;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.classes.Scheduler;

public class RestartSafelyTask implements Runnable {

    @Override
    public void run() {

        Scheduler.cancelTasks(Common.plugin);
        Bukkit.getServer().spigot().restart();
    }
}
