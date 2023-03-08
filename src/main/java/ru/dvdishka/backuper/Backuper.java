package ru.dvdishka.backuper;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.common.CommonVariables;
import ru.dvdishka.backuper.common.ConfigVariables;
import ru.dvdishka.backuper.common.Initialization;
import ru.dvdishka.backuper.tasks.BackuperStartTask;

public class Backuper extends JavaPlugin {

    public void onEnable() {

        CommonVariables.plugin = this;

        File pluginDir = new File("plugins/Backuper");
        File backupsDir = new File("plugins/Backuper/Backups");
        File configFile = new File("plugins/Backuper/config.yml");

        CommonVariables.logger = getLogger();

        if (!pluginDir.exists()) {

            pluginDir.mkdir();
        }

        if (!backupsDir.exists()) {

            backupsDir.mkdir();
        }

        if (configFile.exists()) {

            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            ConfigVariables.backupTime = config.getInt("backupTime");
            ConfigVariables.backupPeriod = config.getInt("backupPeriod");
            ConfigVariables.afterBackup = config.getString("afterBackup").toLowerCase();
            ConfigVariables.backupsNumber = config.getInt("maxBackupsNumber");
            ConfigVariables.backupsWeight = config.getLong("maxBackupsWeight") * 1_048_576L;

        } else {

            try {

                this.saveDefaultConfig();

            } catch (Exception e) {

                CommonVariables.logger.warning("Something went wrong when trying to create config file!");
                CommonVariables.logger.warning(e.getMessage());
            }
        }

        int delay = 0;

        if (ConfigVariables.backupTime > LocalDateTime.now().getHour()) {

            delay = ConfigVariables.backupTime * 60 * 60 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());

        } else {

            delay = ConfigVariables.backupTime * 60 * 60 + 86400 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());
        }

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new BackuperStartTask(), (long) delay * 20, ConfigVariables.backupPeriod * 60L * 60L * 20L);

        Initialization.initBstats(this);

        CommonVariables.logger.info("Backuper plugin has been enabled!");
    }

    public void onDisable() {

        Bukkit.getScheduler().cancelTasks(this);

        CommonVariables.logger.info("Backuper plugin has been disabled!");
    }
}