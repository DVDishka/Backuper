package ru.dvdishka.backuper.common;

import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Initialization {

    public static void initBStats(JavaPlugin plugin) {

        Metrics bStats = new Metrics(plugin, CommonVariables.bstatsId);
    }

    public static void initConfig(File configFile) {

        if (configFile.exists()) {

            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

            String configVersion = config.getString("configVersion");

            if (configVersion.equals(ConfigVariables.configVersion)) {

                ConfigVariables.backupTime = config.getInt("backupTime");
                ConfigVariables.backupPeriod = config.getInt("backupPeriod");
                ConfigVariables.afterBackup = config.getString("afterBackup").toUpperCase();
                ConfigVariables.backupsNumber = config.getInt("maxBackupsNumber");
                ConfigVariables.backupsWeight = config.getLong("maxBackupsWeight") * 1_048_576L;
                ConfigVariables.zipArchive = config.getBoolean("zipArchive");

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

                configFile.delete();

                CommonVariables.plugin.saveDefaultConfig();
                FileConfiguration newConfig = CommonVariables.plugin.getConfig();

                newConfig.set("backupTime", ConfigVariables.backupTime);
                newConfig.set("backupPeriod", ConfigVariables.backupPeriod);
                newConfig.set("afterBackup", ConfigVariables.afterBackup);
                newConfig.set("maxBackupsNumber", ConfigVariables.backupsNumber);
                newConfig.set("maxBackupsWeight", ConfigVariables.backupsWeight);
                newConfig.set("zipArchive", ConfigVariables.zipArchive);

                try {

                    newConfig.save(configFile);

                } catch (Exception e) {

                    CommonVariables.logger.warning("Can not save config!");
                    CommonVariables.logger.warning(e.getMessage());
                }
            }

        } else {

            try {

                CommonVariables.plugin.saveDefaultConfig();

            } catch (Exception e) {

                CommonVariables.logger.warning("Something went wrong when trying to create config file!");
                CommonVariables.logger.warning(e.getMessage());
            }
        }
    }
}
