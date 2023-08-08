package ru.dvdishka.backuper.common;

import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.common.classes.Logger;
import ru.dvdishka.backuper.common.classes.Permissions;
import ru.dvdishka.backuper.commands.backup.Backup;
import ru.dvdishka.backuper.commands.list.List;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Objects;

public class Initialization {

    public static void initBStats(JavaPlugin plugin) {

        Metrics bStats = new Metrics(plugin, Common.bStatsId);
    }

    public static void initConfig(File configFile) {

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
            ConfigVariables.autoBackupOnShutDown = config.getBoolean("autoBackupOnShutDown", false);

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
            if (!config.contains("autoBackupOnShutDown")) {
                isConfigFileOk = false;
            }

            if (!isConfigFileOk) {

                if (!configFile.delete()) {
                    Logger.getLogger().devWarn("Initialization", "Can not delete old config file!");
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
                newConfig.set("autoBackupOnShutDown", ConfigVariables.autoBackupOnShutDown);

                try {

                    newConfig.save(configFile);

                } catch (Exception e) {

                    Logger.getLogger().warn("Can not save config!");
                    Logger.getLogger().devWarn("Initialization", e.getMessage());
                }
            }

        } else {

            try {

                Common.plugin.saveDefaultConfig();

            } catch (Exception e) {

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
        ;

        backupCommandTree.register();
    }

    public static void initEventHandlers() {
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
                Logger.getLogger().log("You are using the latest version of Backuper!");
            } else {
                Logger.getLogger().warn("You are using an outdated version of Backuper, please update it to the latest!\nDownload link: " + Common.downloadLink);
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().devWarn("Initialization", e.getStackTrace().toString());
        }
    }
}
