package ru.dvdishka.backuper.common;

public class ConfigVariables {

    public static final String configVersion = "4.0";
    public static long lastBackup = 0;

    public static String backupsFolder = "plugins/Backuper/Backups";
    public static boolean autoBackupOnShutDown = false;
    public static boolean fixedBackupTime = true;
    public static boolean autoBackup = true;
    public static int firstBackupTime = 0;
    public static int backupPeriod = 24;
    public static String afterBackup = "NOTHING";
    public static int backupsNumber = 7;
    public static long backupsWeight = 0;
    public static boolean zipArchive = true;
    public static boolean betterLogging = false;
}