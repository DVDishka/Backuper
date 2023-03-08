package ru.dvdishka.backuper.common;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class Initialization {

    public static void initBstats(JavaPlugin plugin) {

        Metrics bStats = new Metrics(plugin, CommonVariables.bstatsId);
    }
}
