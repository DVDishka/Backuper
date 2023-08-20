package ru.dvdishka.backuper.common;

import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.commands.common.Scheduler;
import ru.dvdishka.backuper.commands.menu.Menu;
import ru.dvdishka.backuper.commands.menu.delete.Delete;
import ru.dvdishka.backuper.commands.menu.delete.DeleteConfirmation;
import ru.dvdishka.backuper.commands.reload.Reload;
import ru.dvdishka.backuper.commands.common.Permissions;
import ru.dvdishka.backuper.commands.backup.Backup;
import ru.dvdishka.backuper.commands.list.List;
import ru.dvdishka.backuper.tasks.BackupStarterTask;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Initialization implements Listener {

    public static void initBStats(JavaPlugin plugin) {
        Metrics bStats = new Metrics(plugin, Common.bStatsId);
    }

    public static void initAutoBackup() {

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

            Scheduler.getScheduler().runSyncRepeatingTask(Common.plugin, new BackupStarterTask(ConfigVariables.afterBackup, true), delay * 20, ConfigVariables.backupPeriod * 60L * 60L * 20L);
        }
    }

    public static void initConfig(File configFile, CommandSender sender) {

        boolean noErrors = true;

        if (configFile.exists()) {

            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            String configVersion = config.getString("configVersion");

            assert configVersion != null;

            ConfigVariables.firstBackupTime = config.getInt("firstBackupTime", 0);
            ConfigVariables.backupPeriod = config.getInt("backupPeriod", 24);
            ConfigVariables.afterBackup = config.getString("afterBackup", "NOTHING").toUpperCase();
            ConfigVariables.backupsNumber = config.getInt("maxBackupsNumber", 7);
            ConfigVariables.backupsWeight = config.getLong("maxBackupsWeight", 0) * 1_048_576L;
            ConfigVariables.zipArchive = config.getBoolean("zipArchive", true);
            ConfigVariables.betterLogging = config.getBoolean("betterLogging", false);
            ConfigVariables.autoBackup = config.getBoolean("autoBackup", true);
            ConfigVariables.lastBackup = config.getLong("lastBackup", 0);
            ConfigVariables.fixedBackupTime = config.getBoolean("fixedBackupTime", true);
            ConfigVariables.backupsFolder = config.getString("backupsFolder", "plugins/Backuper/Backups");

            boolean isConfigFileOk = true;

            if (!configVersion.equals(ConfigVariables.configVersion)) {
                isConfigFileOk = false;
            }
            if (!config.contains("firstBackupTime")) {
                isConfigFileOk = false;
            }
            if (!config.contains("backupPeriod")) {
                isConfigFileOk = false;
            }
            if (!config.contains("afterBackup")) {
                isConfigFileOk = false;
            }
            if (!config.contains("maxBackupsNumber")) {
                isConfigFileOk = false;
            }
            if (!config.contains("maxBackupsWeight")) {
                isConfigFileOk = false;
            }
            if (!config.contains("zipArchive")) {
                isConfigFileOk = false;
            }
            if (!config.contains("betterLogging")) {
                isConfigFileOk = false;
            }
            if (!config.contains("autoBackup")) {
                isConfigFileOk = false;
            }
            if (!config.contains("lastBackup")) {
                isConfigFileOk = false;
            }
            if (!config.contains("fixedBackupTime")) {
                isConfigFileOk = false;
            }
            if (!config.contains("backupsFolder")) {
                isConfigFileOk = false;
            }

            if (!isConfigFileOk) {

                if (!configFile.delete()) {
                    if (sender != null) {
                        Common.returnFailure("Can not delete old config file!", sender);
                    }
                    Logger.getLogger().warn("Can not delete old config file!");
                    noErrors = false;
                }

                Common.plugin.saveDefaultConfig();
                FileConfiguration newConfig = Common.plugin.getConfig();

                newConfig.set("firstBackupTime", ConfigVariables.firstBackupTime);
                newConfig.set("backupPeriod", ConfigVariables.backupPeriod);
                newConfig.set("afterBackup", ConfigVariables.afterBackup);
                newConfig.set("maxBackupsNumber", ConfigVariables.backupsNumber);
                newConfig.set("maxBackupsWeight", ConfigVariables.backupsWeight);
                newConfig.set("zipArchive", ConfigVariables.zipArchive);
                newConfig.set("betterLogging", ConfigVariables.betterLogging);
                newConfig.set("autoBackup", ConfigVariables.autoBackup);
                newConfig.set("lastBackup", ConfigVariables.lastBackup);
                newConfig.set("fixedBackupTime", ConfigVariables.fixedBackupTime);
                newConfig.set("backupsFolder", ConfigVariables.backupsFolder);

                try {

                    newConfig.save(configFile);

                } catch (Exception e) {

                    if (sender != null) {
                        Common.returnFailure("Can not save config file!", sender);
                    }
                    Logger.getLogger().warn("Can not save config file!");
                    Logger.getLogger().devWarn("Initialization", e);
                    noErrors = false;
                }
            }

            if (sender != null && noErrors) {
                Common.returnSuccess("Config has been reloaded successfully!", sender);
                Logger.getLogger().log("Config has been reloaded successfully!");
            }
            if (sender != null && !noErrors) {
                Common.returnFailure("Config has been reloaded with errors!", sender);
                Logger.getLogger().log("Config has been reloaded with errors!");
            }

        } else {

            try {

                Common.plugin.saveDefaultConfig();

            } catch (Exception e) {

                if (sender != null) {
                    Common.returnFailure("Something went wrong when trying to create config file!", sender);
                }
                Logger.getLogger().warn("Something went wrong when trying to create config file!");
                Logger.getLogger().devWarn("Initialization", e.getMessage());
            }
        }
    }

    public static void initCommands() {

        CommandTree backupCommandTree = new CommandTree("backup")
                .withPermission(Permissions.BACKUP.getPermission());

        backupCommandTree.executes((sender, args) -> {

            new Backup().execute(sender, args);

        })

                .then(new LiteralArgument("STOP").withPermission(Permissions.STOP.getPermission())

                        .executes((sender, args) -> {

                            new Backup("STOP").execute(sender, args);
                        })
                )

                .then(new LiteralArgument("RESTART").withPermission(Permissions.RESTART.getPermission())

                        .executes((sender, args) -> {

                            new Backup("RESTART").execute(sender, args);
                        })
                )

                .then(new LiteralArgument("list").withPermission(Permissions.LIST.getPermission())

                        .executes((sender, args) -> {

                            new List().execute(sender, args);
                        })

                        .then(new IntegerArgument("pageNumber").withPermission(Permissions.LIST.getPermission())

                                .executes((sender, args) -> {

                                    new List().execute(sender, args);
                                })
                        )
                )

                .then(new LiteralArgument("reload").withPermission(Permissions.RELOAD.getPermission())

                        .executes((sender, args) -> {

                            new Reload().execute(sender, args);
                        })
                )

                .then(new LiteralArgument("menu").withPermission(Permissions.LIST.getPermission())

                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollection((info) -> {

                            ArrayList<LocalDateTime> backups = new ArrayList<>();
                            for (File file : new File(ConfigVariables.backupsFolder).listFiles()) {
                                backups.add(LocalDateTime.parse(file.getName().replace(".zip", ""), Common.dateTimeFormatter));
                            }

                            Common.sortLocalDateTimeDecrease(backups);
                            ArrayList<String> backupSuggestions = new ArrayList<>();

                            for (LocalDateTime backupName : backups) {
                                backupSuggestions.add("\"" + backupName.format(Common.dateTimeFormatter) + "\"");
                            }
                            return backupSuggestions;
                        }))

                                .executes((sender, args) -> {

                                    new Menu().execute(sender, args);
                                })
                                        .then(new StringArgument("delete")
                                                .withPermission(Permissions.DELETE.getPermission())
                                                .replaceSuggestions(ArgumentSuggestions.empty())

                                                .executes((sender, args) -> {

                                                    if (args.get("delete").equals("deleteConfirmation")) {
                                                        new DeleteConfirmation().execute(sender, args);
                                                    }

                                                    if (args.get("delete").equals("delete")) {
                                                        new Delete().execute(sender, args);
                                                    }
                                                })
                                        )
                        )
                )
        ;

        backupCommandTree.register();
    }

    public static void initEventHandlers() {

        Bukkit.getPluginManager().registerEvents(new Initialization(), Common.plugin);
    }

    public static void checkDependencies() {

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Common.isFolia = true;
            Logger.getLogger().devLog("Folia/Paper has been detected!");
        } catch (Exception e) {
            Common.isFolia = false;
            Logger.getLogger().devLog("Folia/Paper has not been detected!");
        }
    }

    public static void checkOperatingSystem() {
        if (Common.isWindows) {
            Common.dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH;mm;ss");
        }
    }

    public static void checkVersion() {

        try {

            HttpURLConnection connection = (HttpURLConnection) Common.getLatestVersionURL.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String input;
            StringBuilder response = new StringBuilder();

            while ((input = in.readLine()) != null) {
                response.append(input);
            }
            in.close();

            if (response.toString().equals(Common.getProperty("version"))) {
                Common.isUpdatedToLatest = true;
                Logger.getLogger().log("You are using the latest version of Backuper!");
            } else {

                Common.isUpdatedToLatest = false;

                String message = "You are using an outdated version of Backuper, please update it to the latest!";
                for (String downloadLink : Common.downloadLinks) {
                    message = message.concat("\nDownload link: " + downloadLink);
                }

                Logger.getLogger().warn(message);
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().devWarn("Initialization", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        if (event.getPlayer().isOp() && !Common.isUpdatedToLatest) {

            Component message = Component.empty();

            message = message
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)))
                    .appendNewline();

            message = message
                    .append(Component.text("You are using an outdated version of Backuper!\nPlease update it to the latest!")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED));

            int downloadLLinkNumber = 0;
            for (String downloadLink : Common.downloadLinks) {

                message = message.appendNewline();

                message = message
                        .append(Component.text("Download link: " + Common.downloadLinksName.get(downloadLLinkNumber))
                                .clickEvent(ClickEvent.openUrl(downloadLink))
                                .decorate(TextDecoration.UNDERLINED));

                downloadLLinkNumber++;
            }

            message = message
                    .appendNewline()
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)));

            event.getPlayer().sendMessage(message);
        }
    }
}
