package ru.dvdishka.backuper.common;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.bukkit.plugin.Plugin;
import ru.dvdishka.backuper.common.classes.Logger;

public class Common {

    public static Plugin plugin;
    public static Properties properties = new Properties();
    static {
        try {
            properties.load(Common.class.getClassLoader().getResourceAsStream("project.properties"));
        } catch (Exception e) {
            Logger.getLogger().devWarn("Failed to load properties!");
            Logger.getLogger().devWarn(e.getMessage());        }
    }

    public static final int bStatsId = 17735;
    public static final String downloadLink = "https://hangar.papermc.io/Collagen/Backuper";
    public static URL getLatestVersionURL = null;
    static {
        try {
            getLatestVersionURL = new URL("https://hangar.papermc.io/api/v1/projects/Collagen/Backuper/latestrelease");
        } catch (MalformedURLException e) {
            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().devWarn(e.getMessage());
        }
    }

    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public static boolean isFolia = false;

    public static String getProperty(String property) {
        return properties.getProperty(property);
    }
}
