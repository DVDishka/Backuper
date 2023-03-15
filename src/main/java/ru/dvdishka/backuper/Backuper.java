package ru.dvdishka.backuper;

import java.io.File;
import java.time.LocalDateTime;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.common.CommonVariables;
import ru.dvdishka.backuper.common.ConfigVariables;
import ru.dvdishka.backuper.common.Initialization;
import ru.dvdishka.backuper.tasks.BackupStarterTask;

public class Backuper extends JavaPlugin {

    public void onEnable() {

        CommandAPI.onEnable(this);

        CommonVariables.plugin = this;

        File pluginDir = new File("plugins/Backuper");
        File backupsDir = new File("plugins/Backuper/Backups");
        File configFile = new File("plugins/Backuper/config.yml");

        CommonVariables.logger = getLogger();

        if (!pluginDir.exists()) {

            if (!pluginDir.mkdir()) {

                CommonVariables.logger.warning("Can not create plugins/Backuper dir!");
            }
        }

        if (!backupsDir.exists()) {

            if (!backupsDir.mkdir()) {

                CommonVariables.logger.warning("Can not create plugins/Backuper/Backups dir!");
            }
        }

        Initialization.initConfig(configFile);
        Initialization.initBStats(this);
        Initialization.initCommands();

        int delay;

        if (ConfigVariables.backupTime > LocalDateTime.now().getHour()) {

            delay = ConfigVariables.backupTime * 60 * 60 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());

        } else {

            delay = ConfigVariables.backupTime * 60 * 60 + 86400 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new BackupStarterTask(ConfigVariables.afterBackup), (long) delay * 20, ConfigVariables.backupPeriod * 60L * 60L * 20L);

        CommonVariables.logger.info("Backuper plugin has been enabled!");
    }

    public void onLoad() {

        CommandAPI.onLoad(new CommandAPIConfig());
    }

    public void onDisable() {

        Bukkit.getScheduler().cancelTasks(this);

        CommandAPI.onDisable();

        CommonVariables.logger.info("Backuper plugin has been disabled!");
    }
}