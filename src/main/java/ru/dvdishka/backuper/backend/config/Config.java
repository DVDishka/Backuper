package ru.dvdishka.backuper.backend.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;

public interface Config {

    Config load(ConfigurationSection config);

    ConfigurationSection getDefaultConfig();

    ConfigurationSection getConfig();

    /***
     * Not in-place for ConfigurationSection. Updates config's ConfigurationSection field
     */
    default ConfigurationSection repair(ConfigurationSection config) {
        ConfigurationSection defaultConfig = getDefaultConfig();
        for (String key : defaultConfig.getKeys(true)) {
            if (config.contains(key) && !(config.get(key) instanceof MemorySection)) {
                defaultConfig.set(key, config.get(key));
            }
        }
        for (String key : config.getKeys(true)) {
            if (!defaultConfig.contains(key)) {
                defaultConfig.set(key, config.get(key));
            }
        }
        return defaultConfig;
    }

    default Config repairThenLoad(ConfigurationSection config) {
        org.bukkit.configuration.ConfigurationSection repairedConfig = config.createSection(config.getName(), repair(config).getValues(true));
        return load(repairedConfig);
    }
}
