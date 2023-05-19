package ru.dvdishka.backuper;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.common.CommonVariables;
import ru.dvdishka.backuper.common.ConfigVariables;
import ru.dvdishka.backuper.common.Initialization;
import ru.dvdishka.backuper.common.classes.Logger;
import ru.dvdishka.backuper.common.classes.Scheduler;
import ru.dvdishka.backuper.tasks.BackupStarterTask;

public class Backuper extends JavaPlugin {

    public void onEnable() {

        CommandAPI.onEnable();

        CommonVariables.plugin = this;

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

        Initialization.initConfig(configFile);
        Initialization.initBStats(this);
        Initialization.initCommands();
        Initialization.initDependencies();

        if (CommonVariables.isWindows) {
            CommonVariables.dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH;mm;ss");
        }

        if (ConfigVariables.autoBackup) {

            long delay = 0;

            if (ConfigVariables.lastBackup == 0 || ConfigVariables.fixedBackupTime) {
                if (ConfigVariables.firstBackupTime > LocalDateTime.now().getHour()) {

                    delay = (long) ConfigVariables.firstBackupTime * 60 * 60 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());

                } else {

                    delay = (long) ConfigVariables.firstBackupTime * 60 * 60 + 86400 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());
                }
            } else {
                delay = ConfigVariables.backupPeriod * 60L * 60L - (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - ConfigVariables.lastBackup);
            }

            if (delay <= 0) {
                delay = 1;
            }

            Logger.getLogger().devLog("Delay: " + delay);

            Scheduler.getScheduler().runSyncRepeatingTask(this, new BackupStarterTask(ConfigVariables.afterBackup, true), (long) delay * 20, ConfigVariables.backupPeriod * 60L * 60L * 20L);
        }
        Logger.getLogger().log("Backuper plugin has been enabled!");
    }

    public void onLoad() {

        CommandAPI.onLoad(new CommandAPIBukkitConfig(this));
    }

    public void onDisable() {

        Scheduler.cancelTasks(this);

        CommandAPI.onDisable();

        Logger.getLogger().log("Backuper plugin has been disabled!");
    }
}