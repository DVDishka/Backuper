package ru.dvdishka.backuper.backend.config;

import org.bukkit.configuration.ConfigurationSection;

public interface Config {

    Config load(ConfigurationSection config);

    ConfigurationSection getDefaultConfig();

    default void repair(ConfigurationSection config) {
        getDefaultConfig().getKeys(false).forEach(key -> {
            config.addDefault(key, getDefaultConfig().get(key));
        });
    }

    default Config repairThenLoad(ConfigurationSection config) {
        repair(config);
        return load(config);
    }
}
