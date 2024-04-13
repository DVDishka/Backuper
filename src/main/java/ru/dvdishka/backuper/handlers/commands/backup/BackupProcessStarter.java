package ru.dvdishka.backuper.handlers.commands.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.backend.common.Logger;

import java.io.File;
import java.util.HashMap;

public class BackupProcessStarter implements Runnable {

    private final String afterBackup;
    private CommandSender sender = null;
    private boolean isAutoBackup = false;

    public static volatile HashMap<String, Boolean> isAutoSaveEnabled = new HashMap<>();
    public static volatile boolean errorSetWritable = false;

    @SuppressWarnings("unused")
    public BackupProcessStarter(String afterBackup) {

        this.afterBackup = afterBackup;
    }

    public BackupProcessStarter(String afterBackup, boolean isAutoBackup) {

        this.afterBackup = afterBackup;
        this.isAutoBackup = isAutoBackup;
    }

    public BackupProcessStarter(String afterBackup, CommandSender sender) {

        this.afterBackup = afterBackup;
        this.sender = sender;
    }

    public void run() {

        try {

            if (isAutoBackup && Backup.isLocked()) {
                Logger.getLogger().warn("Failed to start an automatic backup because the previous process is not completed", sender);
                return;
            }
            if (Backup.isLocked()) {
                Logger.getLogger().warn("Failed to start the backup because the previous process is not completed", sender);
                return;
            }

            if (Config.getInstance().isSkipDuplicateBackup() && isAutoBackup && Config.getInstance().getLastBackup() > Config.getInstance().getLastChange()) {

                Logger.getLogger().warn("The backup cycle will be skipped since there were no changes from the previous backup", sender);
                Config.getInstance().updateLastBackup();

                if (afterBackup.equals("RESTART")) {

                    Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {
                        Scheduler.cancelTasks(Utils.plugin);
                        Bukkit.getServer().spigot().restart();
                    }, 20);

                } else if (afterBackup.equals("STOP")) {

                    Logger.getLogger().devLog("Stopping server...");
                    Bukkit.shutdown();
                }
                return;
            }

            Logger.getLogger().log("Backup process has been started", sender);

            BackupProcess backupProcess = new BackupProcess("Backup", afterBackup, isAutoBackup, sender);
            Backup.lock(backupProcess);

            setWorldsReadOnlySync(sender);

            Scheduler.getScheduler().runAsync(Utils.plugin, backupProcess);

        } catch (Exception e) {

            Backup.unlock();

            setWorldsWritableSync(sender, false);

            Logger.getLogger().warn("Backup process has been finished with an exception!", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    public void runDeleteOldBackupsSync() {

        if (Backup.isLocked()) {
            Logger.getLogger().warn("Failed to start deleteOldBackup task because the previous process is not completed");
            return;
        }

        new BackupProcess("DeleteOldBackups", afterBackup, isAutoBackup, sender).deleteOldBackups(new File(Config.getInstance().getBackupsFolder()), true);
    }

    public static void setWorldsReadOnlySync(CommandSender sender, boolean force) {

        if (Config.getInstance().isNotSetReadOnly() && !force) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {

            if (!errorSetWritable) {
                isAutoSaveEnabled.put(world.getName(), world.isAutoSave());
            }

            world.setAutoSave(false);
            if (!world.getWorldFolder().setReadOnly()) {
                Logger.getLogger().warn("Can not set folder read only!", sender);
            }
        }
    }

    public static void setWorldsReadOnlySync(CommandSender sender) {

        if (Config.getInstance().isNotSetReadOnly()) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {

            if (!errorSetWritable) {
                isAutoSaveEnabled.put(world.getName(), world.isAutoSave());
            }

            world.setAutoSave(false);
            if (!world.getWorldFolder().setReadOnly()) {
                Logger.getLogger().warn("Can not set folder read only!", sender);
            }
        }
    }

    public static void setWorldsWritableSync(CommandSender sender, boolean force) {

        if (Config.getInstance().isNotSetReadOnly() && !force) {
            return;
        }

        errorSetWritable = false;

        for (World world : Bukkit.getWorlds()) {

            if (!world.getWorldFolder().setWritable(true)) {
                Logger.getLogger().warn("Can not set " + world.getWorldFolder().getPath() + " writable!", sender);
                errorSetWritable = true;
            }

            if (isAutoSaveEnabled.containsKey(world.getName())) {
                world.setAutoSave(force || isAutoSaveEnabled.get(world.getName()));
            }
        }
    }
}