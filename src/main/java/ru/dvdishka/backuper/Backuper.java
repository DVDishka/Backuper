package ru.dvdishka.backuper;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.backend.Bstats;
import ru.dvdishka.backuper.backend.LogManager;
import ru.dvdishka.backuper.backend.ScheduleManager;
import ru.dvdishka.backuper.backend.autobackup.AutoBackupScheduleManager;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.backend.storage.StorageManager;
import ru.dvdishka.backuper.backend.task.SetWorldsWritableTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.task.TaskManager;
import ru.dvdishka.backuper.backend.util.AdminInfoUtils;
import ru.dvdishka.backuper.backend.util.Utils;
import ru.dvdishka.backuper.handlers.AdminJoinHandler;
import ru.dvdishka.backuper.handlers.commands.CommandManager;
import ru.dvdishka.backuper.handlers.worldchangecatch.WorldChangeCatcherNew;

import java.io.File;

@Getter
public class Backuper extends JavaPlugin {

    TaskManager taskManager;
    LogManager logManager;
    ConfigManager configManager;
    ScheduleManager scheduleManager;
    StorageManager storageManager;
    CommandManager commandManager;
    AutoBackupScheduleManager autoBackupScheduleManager;
    Bstats bstats;

    @Getter
    private static Backuper instance;

    public static boolean restarting = false;

    public void onEnable() {
        instance = this;
        CommandAPI.onEnable();
        registerEventHandlers();
        init();
        commandManager.init(); // Shouldn't be reloaded with plugin reload using command /backuper config reload
        scheduleManager.runAsync(() -> { // Very big performance impact if run it on every reload
            storageManager.indexStorages();
        });

        // We shouldn't really send all this information on every config reload
        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            AdminInfoUtils.sendIssueToGitHub(Bukkit.getConsoleSender());
            AdminInfoUtils.sendPluginVersionCheck(Bukkit.getConsoleSender());
        });

        Backuper.getInstance().getLogManager().log("Backuper plugin has been enabled!");
    }

    public void init() {
        this.configManager = new ConfigManager();
        this.logManager = new LogManager();
        this.taskManager = new TaskManager();
        this.scheduleManager = new ScheduleManager();
        this.taskManager.forceLock();
        this.storageManager = new StorageManager();
        this.commandManager = new CommandManager();
        this.autoBackupScheduleManager = new AutoBackupScheduleManager();
        this.bstats = new Bstats();

        File pluginDir = new File("plugins/Backuper");
        File configFile = new File("plugins/Backuper/config.yml");
        if (!pluginDir.exists() && !pluginDir.mkdirs()) Backuper.getInstance().getLogManager().warn("Can not create plugins/Backuper dir!");

        configManager.load(configFile, Bukkit.getConsoleSender());
        scheduleManager.init(); // Should be initialized after the config file
        scheduleManager.runAsync(() -> {
            storageManager.loadSizeCache();
            storageManager.checkStoragesConnection();
        });
        bstats.init(this);
        checkDependencies();
        taskManager.forceUnlock();
        autoBackupScheduleManager.init();
    }

    public void shutdown() {
        taskManager.forceLock();
        storageManager.saveSizeCache();
        Task setWorldsWritableTask = new SetWorldsWritableTask();
        try {
            getTaskManager().startTaskRaw(setWorldsWritableTask, Bukkit.getConsoleSender());
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn(e);
        }
        Backuper.getInstance().getScheduleManager().destroy(this);
        configManager.setConfigField("lastBackup", configManager.getLastBackup());
        configManager.setConfigField("lastChange", configManager.getLastChange());
        storageManager.destroy();
        autoBackupScheduleManager.destroy();
        scheduleManager.destroy(this);
        bstats.destroy();
    }

    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIPaperConfig(this).fallbackToLatestNMS(true));
    }

    public void onDisable() {
        shutdown();
        CommandAPI.onDisable();
        Backuper.getInstance().getLogManager().log("Backuper plugin has been disabled!");
    }

    private void registerEventHandlers() {

        Bukkit.getPluginManager().registerEvents(new AdminJoinHandler(), Backuper.getInstance());
        Bukkit.getPluginManager().registerEvents(new StorageManager(), Backuper.getInstance());

        boolean doWorldChangeEventExist = true;
        for (String eventName : WorldChangeCatcherNew.eventNames) {
            try {
                Class.forName(eventName);
            } catch (Exception e) {
                doWorldChangeEventExist = false;
            }
        }
        if (doWorldChangeEventExist) {
            Bukkit.getPluginManager().registerEvents(new WorldChangeCatcherNew(), Backuper.getInstance());
        }
    }

    private void checkDependencies() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Utils.isFolia = true;
            Backuper.getInstance().getLogManager().devLog("Folia/Paper(1.20+) has been detected!");
        } catch (Exception e) {
            Utils.isFolia = false;
            Backuper.getInstance().getLogManager().devLog("Folia/Paper(1.20+) has not been detected!");
        }
    }
}