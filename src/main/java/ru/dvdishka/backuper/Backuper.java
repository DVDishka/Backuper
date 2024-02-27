package ru.dvdishka.backuper;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.backend.utils.Scheduler;
import ru.dvdishka.backuper.backend.utils.Common;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.backend.utils.Logger;
import ru.dvdishka.backuper.handlers.commands.backup.BackupProcessStarter;

import java.io.File;

public class Backuper extends JavaPlugin {

    public void onEnable() {

        CommandAPI.onEnable();

        Common.plugin = this;

        File pluginDir = new File("plugins/Backuper");
        File backupsDir = new File("plugins/Backuper/Backups");
        File configFile = new File("plugins/Backuper/config.yml");

        if (!pluginDir.exists()) {

            if (!pluginDir.mkdir()) {

                Logger.getLogger().warn("Can not create plugins/Backuper dir!");
            }
        }

        if (!backupsDir.exists()) {

            if (!backupsDir.mkdir()) {

                Logger.getLogger().warn("Can not create plugins/Backuper/Backups dir!");
            }
        }

        Initialization.initConfig(configFile, null);
        Initialization.initBStats(this);
        Initialization.initCommands();
        Initialization.initEventHandlers();
        Initialization.checkDependencies();
        Initialization.checkPluginVersion();
        Initialization.checkOperatingSystem();

        Logger.getLogger().log("Backuper plugin has been enabled!");
    }

    public void onLoad() {

        CommandAPI.onLoad(new CommandAPIBukkitConfig(this));
    }

    public void onDisable() {

        BackupProcessStarter.setWritable(Bukkit.getConsoleSender(), false);
        Scheduler.cancelTasks(this);

        CommandAPI.onDisable();

        Logger.getLogger().log("Backuper plugin has been disabled!");
    }
}