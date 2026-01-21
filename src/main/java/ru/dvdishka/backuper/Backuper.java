package ru.dvdishka.backuper;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIPaperConfig;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
import ru.dvdishka.backuper.backend.util.UIUtils;
import ru.dvdishka.backuper.backend.util.Utils;
import ru.dvdishka.backuper.handlers.commands.CommandManager;
import ru.dvdishka.backuper.handlers.worldchangecatch.WorldChangeCatcherNew;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

@Getter
public class Backuper extends JavaPlugin implements Listener {

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
        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            checkPluginVersion();
            sendIssueToGitHub(Bukkit.getConsoleSender());
            sendPluginVersionCheckResult(Bukkit.getConsoleSender());
        });
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendPluginVersionCheckResult(event.getPlayer());
    }

    private void registerEventHandlers() {

        Bukkit.getPluginManager().registerEvents(this, Backuper.getInstance());
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

    private boolean checkPluginVersion() {

        if (!Backuper.getInstance().getConfigManager().getServerConfig().isCheckUpdates()) return true;

        try {
            HttpURLConnection connection = (HttpURLConnection) Utils.getLatestVersionURL.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String input;
            StringBuilder response = new StringBuilder();

            while ((input = in.readLine()) != null) {
                response.append(input);
            }
            in.close();

            return response.toString().equals(Utils.getProperty("version"));
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to check the Backuper updates!");
            Backuper.getInstance().getLogManager().warn(e);
            return true; // We shouldn't say that the plugin should be updated if there is some problem during the check
        }
    }

    private void sendPluginVersionCheckResult(CommandSender sender) {
        if (sender.isOp() && !checkPluginVersion()) {
            Component header = Component.empty();
            header = header
                    .append(Component.text("Backuper is outdated")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED));

            Component message = Component.empty();
            message = message
                    .append(Component.text("You are using an outdated version of Backuper!\nPlease update it to the latest and check the changelist!"));

            int downloadLinkNumber = 0;
            for (String downloadLink : Utils.downloadLinks) {
                message = message.append(Component.newline());
                message = message
                        .append(Component.text("Download link:"))
                        .append(Component.space())
                        .append(Component.text(sender instanceof ConsoleCommandSender ? downloadLink : Utils.downloadLinksName.get(downloadLinkNumber))
                                .clickEvent(ClickEvent.openUrl(downloadLink))
                                .decorate(TextDecoration.UNDERLINED));
                downloadLinkNumber++;
            }
            UIUtils.sendFramedMessage(header, message, sender);
        }
    }

    private void sendIssueToGitHub(CommandSender sender) {
        if (!sender.isOp()) return;

        Component header = Component.empty();
        header = header
                .append(Component.text("Issue tracking")
                        .decorate(TextDecoration.BOLD)
                        .color(NamedTextColor.RED));

        Component message = Component.empty();
        message = message
                .append(Component.text("Please, if you find any issues related to the Backuper"))
                .append(Component.newline())
                .append(Component.text("Create an issue using the link:"))
                .append(Component.space())
                .append(Component.text("https://github.com/DVDishka/Backuper/issues")
                        .clickEvent(ClickEvent.openUrl("https://github.com/DVDishka/Backuper/issues"))
                        .decorate(TextDecoration.UNDERLINED));

        UIUtils.sendFramedMessage(header, message, sender);
    }
}