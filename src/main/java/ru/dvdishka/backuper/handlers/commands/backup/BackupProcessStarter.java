package ru.dvdishka.backuper.handlers.commands.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.back.config.Config;
import ru.dvdishka.backuper.back.common.Scheduler;
import ru.dvdishka.backuper.back.common.Backup;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.common.Logger;

import java.util.HashMap;

public class BackupProcessStarter implements Runnable {

    private final String afterBackup;
    private CommandSender sender = null;
    private boolean isAutoBackup = false;
    public static HashMap<String, Boolean> isAutoSaveEnabled = new HashMap<>();

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

            if (isAutoBackup && Backup.isBackupBusy) {
                Logger.getLogger().warn("Failed to start an automatic backup because the previous process is not completed", sender);
                return;
            }
            if (Backup.isBackupBusy) {
                Logger.getLogger().warn("Failed to start the backup because the previous process is not completed", sender);
                return;
            }

            if (Config.getInstance().isSkipDuplicateBackup() && isAutoBackup && Config.getInstance().getLastBackup() > Config.getInstance().getLastChange()) {

                Logger.getLogger().warn("The backup cycle will be skipped since there were no changes from the previous backup", sender);
                Config.getInstance().updateLastBackup();

                if (afterBackup.equals("RESTART")) {

                    Scheduler.getScheduler().runSyncDelayed(Common.plugin, () -> {
                        Scheduler.cancelTasks(Common.plugin);
                        Bukkit.getServer().spigot().restart();
                    }, 20);

                } else if (afterBackup.equals("STOP")) {

                    Logger.getLogger().devLog("Stopping server...");
                    Bukkit.shutdown();
                }
                return;
            }

            Logger.getLogger().log("Backup process has been started", sender);

            Backup.isBackupBusy = true;

            for (World world : Bukkit.getWorlds()) {

                isAutoSaveEnabled.put(world.getName(), world.isAutoSave());

                world.setAutoSave(false);
                if (!world.getWorldFolder().setReadOnly()) {
                    Logger.getLogger().warn("Can not set folder read only!", sender);
                }
            }

            Scheduler.getScheduler().runAsync(Common.plugin, new BackupProcess(afterBackup, isAutoBackup, sender));

        } catch (Exception e) {

            Backup.isBackupBusy = false;

            for (World world : Bukkit.getWorlds()) {
                if (!world.getWorldFolder().setWritable(true)) {
                    Logger.getLogger().warn("Can not set " + world.getWorldFolder().getPath() + " writable!", sender);
                }
                world.setAutoSave(isAutoSaveEnabled.get(world.getName()));
            }

            Logger.getLogger().warn("Backup process has been finished with an exception!", sender);
            Logger.getLogger().devWarn(this, e);
        }
    }
}