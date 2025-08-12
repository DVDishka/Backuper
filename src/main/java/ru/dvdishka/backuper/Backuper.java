package ru.dvdishka.backuper;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.common.LogManager;
import ru.dvdishka.backuper.backend.common.ScheduleManager;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.SetWorldsWritableTask;
import ru.dvdishka.backuper.backend.task.TaskManager;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

public class Backuper extends JavaPlugin {

    private TaskManager taskManager;
    private LogManager logger;
    private ScheduleManager scheduleManager;

    private static Backuper instance;

    public void onEnable() {

        CommandAPI.onEnable();

        instance = this;
        this.logger = new LogManager();
        this.taskManager = new TaskManager();

        File pluginDir = new File("plugins/Backuper");
        File configFile = new File("plugins/Backuper/config.yml");

        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            Backuper.getInstance().getLogManager().warn("Can not create plugins/Backuper dir!");
        }
        
        Initialization.initConfig(configFile, null);
        this.scheduleManager = new ScheduleManager(); // Should be initialized after the config file
        Initialization.loadSizeCache(null);
        Initialization.sendGoogleAccountCheckResult(this.getServer().getConsoleSender());
        Initialization.checkStorages(null);
        Initialization.indexStorages(null);

        File backupsDir = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        if (!backupsDir.exists() && !backupsDir.mkdirs()) {

            Backuper.getInstance().getLogManager().warn("Can not create plugins/Backuper/Backups dir!");
        }

        Initialization.unifyBackupNameFormat(null);
        Initialization.initBStats(this);
        Initialization.initCommands();
        Initialization.initEventHandlers();
        Initialization.checkDependencies();
        Initialization.checkPluginVersion();
        Initialization.sendIssueToGitHub();
        Initialization.sendPluginVersionCheckResult(this.getServer().getConsoleSender());

        Backuper.getInstance().getLogManager().log("Backuper plugin has been enabled!");
    }

    public void onLoad() {

        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).useLatestNMSVersion(false));
    }

    public void onDisable() {

        saveSizeCache();

        Backuper.getInstance().getScheduleManager().destroy(this);
        BaseAsyncTask setWorldsWritableTask = new SetWorldsWritableTask();
        getTaskManager().startTask(setWorldsWritableTask, Bukkit.getConsoleSender(), List.of());

        Config.getInstance().setConfigField("lastBackup", Config.getInstance().getLastBackup());
        Config.getInstance().setConfigField("lastChange", Config.getInstance().getLastChange());

        CommandAPI.onDisable();

        Backuper.getInstance().getLogManager().log("Backuper plugin has been disabled!");
    }

    private void saveSizeCache() {

        try {
            File sizeCachceFile = Config.getInstance().getSizeCacheFile();

            FileWriter writer = new FileWriter(sizeCachceFile);
            writer.write(Backup.getSizeCacheJson());
            writer.close();

        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to save size cache to disk!");
            Backuper.getInstance().getLogManager().warn(e);
        }
    }

    public static Backuper getInstance() {
        return instance;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public LogManager getLogManager() {
        return logger;
    }

    public ScheduleManager getScheduleManager() {
        return scheduleManager;
    }
}