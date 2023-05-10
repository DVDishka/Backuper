package ru.dvdishka.backuper.common;

import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

public class CommonVariables {

    public static Plugin plugin;
    public static Logger logger;
    public static int bStatsId = 17735;
    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    public static final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
}
