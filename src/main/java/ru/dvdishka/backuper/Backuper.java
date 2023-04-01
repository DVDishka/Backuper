package ru.dvdishka.backuper;

import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.common.CommonVariables;
import ru.dvdishka.backuper.common.ConfigVariables;
import ru.dvdishka.backuper.common.Initialization;
import ru.dvdishka.backuper.tasks.BackupStarterTask;

public class Backuper extends JavaPlugin {

    public void onEnable() {

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

        long delay;

        if (ConfigVariables.backupTime > LocalDateTime.now().getHour()) {

            delay = (long) ConfigVariables.backupTime * 60 * 60 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());

        } else {

            delay = (long) ConfigVariables.backupTime * 60 * 60 + 86400 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());
        }

        Bukkit.getAsyncScheduler().runAtFixedRate(this, (task) -> {

            new BackupStarterTask(ConfigVariables.afterBackup).run();
        }, delay, (long) ConfigVariables.backupPeriod * 60 * 60, TimeUnit.SECONDS);

        CommonVariables.logger.info("Backuper plugin has been enabled!");
    }

    public void onDisable() {

        Bukkit.getAsyncScheduler().cancelTasks(this);

        CommonVariables.logger.info("Backuper plugin has been disabled!");
    }
}