package ru.dvdishka.backuperspigot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalDateTime;

public final class BackuperSpigot extends JavaPlugin {

    @Override
    public void onEnable() {

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
            ConfigVariables.lastBackup = config.getInt("lastBackup");

        } else {

            try {

                configFile.createNewFile();

                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

                config.set("backupTime", ConfigVariables.backupTime);
                config.set("backupPeriod", ConfigVariables.backupPeriod);
                config.set("lastBackup", ConfigVariables.lastBackup);

                config.save(configFile);

            } catch (Exception e) {

                CommonVariables.logger.warning("Something went wrong when trying to create config file!");
            }
        }

        long delay = 0;

        if (ConfigVariables.backupTime >= LocalDateTime.now().getHour()) {

            delay = ((long) ConfigVariables.backupTime * 60 * 60) -

                    ((LocalDateTime.now().getHour() * 60 * 60) +
                            (LocalDateTime.now().getMinute() * 60) +
                            (LocalDateTime.now().getSecond()));

        } else {

            delay = ((long) ConfigVariables.backupTime * 60 * 60 + (24 * 60 * 60)) -

                    ((LocalDateTime.now().getHour() * 60 * 60) +
                            (LocalDateTime.now().getMinute() * 60) +
                            (LocalDateTime.now().getSecond()));
        }

        Bukkit.getPluginManager().registerEvents(new EventHandler(), this);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new BackuperTask(), delay * 20L,
                (long) ConfigVariables.backupPeriod * 60 * 60 * 20);

        CommonVariables.logger.info("Backuper plugin has been enabled!");
    }

    @Override
    public void onDisable() {

        Bukkit.getScheduler().cancelTasks(this);
        CommonVariables.logger.info("Backuper plugin has been disabled!");
    }
}
