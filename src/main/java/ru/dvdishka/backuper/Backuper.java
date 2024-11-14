package ru.dvdishka.backuper;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.common.SetWorldsWritableTask;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.util.List;

public class Backuper extends JavaPlugin {

    private static volatile boolean isBackupBusy = false;
    private static Task currentTask = null;

    public static Task getCurrentTask() {
        return currentTask;
    }

    public static void lock(Task task) {
        isBackupBusy = true;
        currentTask = task;
    }

    public static void unlock() {
        isBackupBusy = false;
        currentTask = null;
    }

    public static boolean isLocked() {
        return isBackupBusy;
    }

    public void onEnable() {

        CommandAPI.onEnable();

        Utils.plugin = this;

        File pluginDir = new File("plugins/Backuper");
        File configFile = new File("plugins/Backuper/config.yml");
        
        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            
            Logger.getLogger().warn("Can not create plugins/Backuper dir!");
        }
        
        
        Initialization.initConfig(configFile, null);
        File backupsDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        if (!backupsDir.exists() && !backupsDir.mkdirs()) {

            Logger.getLogger().warn("Can not create plugins/Backuper/Backups dir!");
        }


        Initialization.checkStorages(null);
        Initialization.unifyBackupNameFormat(null);
        Initialization.initBStats(this);
        Initialization.initCommands();
        Initialization.initEventHandlers();
        Initialization.checkDependencies();
        Initialization.checkPluginVersion();
        Initialization.sendIssueToGitHub();

        if(Config.getInstance().getGoogleDriveConfig().isEnabled()) {
            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {
                try {
                    GoogleDriveUtils.returnCredentialIfAuthorized(Bukkit.getConsoleSender());
                    GoogleDriveUtils.test(null);
                } catch (Exception e) {
                    Logger.getLogger().warn(this.getClass(), e);
                }
            });
        }
        Logger.getLogger().log("Backuper plugin has been enabled!");
    }

    public void onLoad() {

        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).useLatestNMSVersion(false));
    }

    public void onDisable() {

        new SetWorldsWritableTask(false, List.of(), null).run();

        Scheduler.cancelTasks(this);

        Config.getInstance().setConfigField("lastBackup", Config.getInstance().getLastBackup());
        Config.getInstance().setConfigField("lastChange", Config.getInstance().getLastChange());

        CommandAPI.onDisable();

        Logger.getLogger().log("Backuper plugin has been disabled!");
    }
}