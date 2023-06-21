package ru.dvdishka.backuper.common;

import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.LiteralArgument;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.common.classes.Logger;
import ru.dvdishka.backuper.common.classes.Permissions;
import ru.dvdishka.backuper.handlers.commands.Backup;

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

            if (configVersion.equals(ConfigVariables.configVersion)) {

                ConfigVariables.firstBackupTime = config.getInt("firstBackupTime");
                ConfigVariables.backupPeriod = config.getInt("backupPeriod");
                ConfigVariables.afterBackup = Objects.requireNonNull(config.getString("afterBackup")).toUpperCase();
                ConfigVariables.backupsNumber = config.getInt("maxBackupsNumber");
                ConfigVariables.backupsWeight = config.getLong("maxBackupsWeight") * 1_048_576L;
                ConfigVariables.zipArchive = config.getBoolean("zipArchive");
                ConfigVariables.betterLogging = config.getBoolean("betterLogging");
                ConfigVariables.autoBackup = config.getBoolean("autoBackup");
                ConfigVariables.lastBackup = config.getLong("lastBackup");
                ConfigVariables.fixedBackupTime = config.getBoolean("fixedBackupTime");
                ConfigVariables.backupsFolder = config.getString("backupsFolder");

            } else {

                if (config.contains("firstBackupTime")) {
                    ConfigVariables.firstBackupTime = config.getInt("firstBackupTime");
                }
                if (config.contains("backupPeriod")) {
                    ConfigVariables.backupPeriod = config.getInt("backupPeriod");
                }
                if (config.contains("afterBackup")) {
                    ConfigVariables.afterBackup = config.getString("afterBackup");
                }
                if (config.contains("maxBackupsNumber")) {
                    ConfigVariables.backupsNumber = config.getInt("maxBackupsNumber");
                }
                if (config.contains("maxBackupsWeight")) {
                    ConfigVariables.backupsWeight = config.getLong("maxBackupsWeight");
                }
                if (config.contains("zipArchive")) {
                    ConfigVariables.zipArchive = config.getBoolean("zipArchive");
                }
                if (config.contains("betterLogging")) {
                    ConfigVariables.betterLogging = config.getBoolean("betterLogging");
                }
                if (config.contains("autoBackup")) {
                    ConfigVariables.autoBackup = config.getBoolean("autoBackup");
                }
                if (config.contains("lastBackup")) {
                    ConfigVariables.lastBackup = config.getLong("lastBackup");
                }
                if (config.contains("fixedBackupTime")) {
                    ConfigVariables.fixedBackupTime = config.getBoolean("fixedBackupTime");
                }
                if (config.contains("backupsFolder")) {
                    ConfigVariables.backupsFolder = config.getString("backupsFolder");
                }

                if (!configFile.delete()) {
                    Logger.getLogger().devWarn("Can not delete old config file!");
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
                newConfig.set("backupsFoler", ConfigVariables.backupsFolder);

                try {

                    newConfig.save(configFile);

                } catch (Exception e) {

                    Logger.getLogger().warn("Can not save config!");
                    Logger.getLogger().devWarn(e.getMessage());
                }
            }

        } else {

            try {

                Common.plugin.saveDefaultConfig();

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong when trying to create config file!");
                Logger.getLogger().devWarn(e.getMessage());
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

                }));

        backupCommandTree.register();
    }

    public static void checkDependencies() {

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Common.isFolia = true;
            Logger.getLogger().devLog("Folia has been detected!");
        } catch (Exception e) {
            Common.isFolia = false;
            Logger.getLogger().devLog("Folia has not been detected!");
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
            Logger.getLogger().devWarn(e.getMessage());
        }
    }
}
