package ru.dvdishka.backuper.common;

import dev.jorel.commandapi.CommandTree;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.handlers.commands.Backup;

public class Initialization {

    public static void initBStats(JavaPlugin plugin) {

        Metrics bStats = new Metrics(plugin, CommonVariables.bstatsId);
    }

    public static void initCommands() {

        CommandTree backupCommandTree = new CommandTree("backup");

        backupCommandTree.executesPlayer((sender, args) -> {

            new Backup().execute(sender, args);
        });

        backupCommandTree.register();
    }
}
