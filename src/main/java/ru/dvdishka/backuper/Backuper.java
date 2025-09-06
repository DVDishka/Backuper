package ru.dvdishka.backuper;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.backend.Initialization;
import ru.dvdishka.backuper.backend.LogManager;
import ru.dvdishka.backuper.backend.ScheduleManager;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.backend.quartzjob.AutoBackupQuartzJob;
import ru.dvdishka.backuper.backend.storage.StorageManager;
import ru.dvdishka.backuper.backend.task.SetWorldsWritableTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.task.TaskManager;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

@Getter
public class Backuper extends JavaPlugin {

    private TaskManager taskManager;
    private LogManager logManager;
    private ConfigManager configManager;
    private ScheduleManager scheduleManager;
    private StorageManager storageManager;

    @Getter
    private static Backuper instance;

    public void onEnable() {

        CommandAPI.onEnable();

        instance = this;
        this.configManager = new ConfigManager();
        this.logManager = new LogManager();
        this.taskManager = new TaskManager();
        this.storageManager = new StorageManager();

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

        storageManager.saveSizeCache();

        Backuper.getInstance().getScheduleManager().destroy(this);
        Task setWorldsWritableTask = new SetWorldsWritableTask();
        getTaskManager().startTask(setWorldsWritableTask, Bukkit.getConsoleSender(), List.of());

        configManager.setConfigField("lastBackup", configManager.getLastBackup());
        configManager.setConfigField("lastChange", configManager.getLastChange());

        CommandAPI.onDisable();

        Backuper.getInstance().getLogManager().log("Backuper plugin has been disabled!");
    }

    @EventHandler
    public void onStartCompleted(ServerLoadEvent event) {
        new AutoBackupQuartzJob().init();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Initialization.sendPluginVersionCheckResult(event.getPlayer());
        Initialization.sendGoogleAccountCheckResult(event.getPlayer());
    }
}