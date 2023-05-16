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

import java.io.File;
import java.util.Objects;

public class Initialization {

    public static void initBStats(JavaPlugin plugin) {

        Metrics bStats = new Metrics(plugin, CommonVariables.bStatsId);
    }

    public static void initConfig(File configFile) {

        if (configFile.exists()) {

            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            String configVersion = config.getString("configVersion");

            assert configVersion != null;

            if (configVersion.equals(ConfigVariables.configVersion)) {

                ConfigVariables.backupTime = config.getInt("backupTime");
                ConfigVariables.backupPeriod = config.getInt("backupPeriod");
                ConfigVariables.afterBackup = Objects.requireNonNull(config.getString("afterBackup")).toUpperCase();
                ConfigVariables.backupsNumber = config.getInt("maxBackupsNumber");
                ConfigVariables.backupsWeight = config.getLong("maxBackupsWeight") * 1_048_576L;
                ConfigVariables.zipArchive = config.getBoolean("zipArchive");
                ConfigVariables.betterLogging = config.getBoolean("betterLogging");

            } else {

                if (config.contains("backupTime")) {

                    ConfigVariables.backupTime = config.getInt("backupTime");
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

                if (!configFile.delete()) {
                    Logger.getLogger().devWarn("Can not delete old config file!");
                }

                CommonVariables.plugin.saveDefaultConfig();
                FileConfiguration newConfig = CommonVariables.plugin.getConfig();

                newConfig.set("backupTime", ConfigVariables.backupTime);
                newConfig.set("backupPeriod", ConfigVariables.backupPeriod);
                newConfig.set("afterBackup", ConfigVariables.afterBackup);
                newConfig.set("maxBackupsNumber", ConfigVariables.backupsNumber);
                newConfig.set("maxBackupsWeight", ConfigVariables.backupsWeight);
                newConfig.set("zipArchive", ConfigVariables.zipArchive);
                newConfig.set("betterLogging", ConfigVariables.betterLogging);

                try {

                    newConfig.save(configFile);

                } catch (Exception e) {

                    Logger.getLogger().warn("Can not save config!");
                    Logger.getLogger().devWarn(e.getMessage());
                }
            }

        } else {

            try {

                CommonVariables.plugin.saveDefaultConfig();

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

    public static void initDependencies() {

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            CommonVariables.isFolia = true;
            Logger.getLogger().devLog("Folia has been detected!");
        } catch (Exception e) {
            CommonVariables.isFolia = false;
            Logger.getLogger().devLog("Folia has not been detected!");
        }
    }
}
