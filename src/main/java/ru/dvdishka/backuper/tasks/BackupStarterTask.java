package ru.dvdishka.backuper.tasks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.Scheduler;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.Logger;

public class BackupStarterTask implements Runnable {

    private final String afterRestart;
    private CommandSender sender = null;
    private boolean isAutoBackup = false;

    @SuppressWarnings("unused")
    public BackupStarterTask(String afterRestart) {

        this.afterRestart = afterRestart;
    }

    public BackupStarterTask(String afterRestart, boolean isAutoBackup) {

        this.afterRestart = afterRestart;
        this.isAutoBackup = isAutoBackup;
    }

    public BackupStarterTask(String afterRestart, CommandSender sender) {

        this.afterRestart = afterRestart;
        this.sender = sender;
    }

    public void run() {

        try {

            Logger.getLogger().log("Backup process has been started!");

            Backup.isBackupBusy = true;

            for (World world : Bukkit.getWorlds()) {
                if (!world.getWorldFolder().setReadOnly()) {
                    Logger.getLogger().devWarn(this, "Can not set folder read only!");
                }
            }

            Scheduler.getScheduler().runAsync(Common.plugin, new BackupTask(afterRestart, isAutoBackup, sender));

        } catch (Exception e) {

            Backup.isBackupBusy = false;

            for (World world : Bukkit.getWorlds()) {

                if (!world.getWorldFolder().setWritable(true)) {

                    Logger.getLogger().devWarn(this, "Can not set " + world.getWorldFolder().getPath() + " writable!");
                }
            }

            Logger.getLogger().warn("Backup process has been finished with an exception!");
            Logger.getLogger().devWarn(this, e);
        }
    }
}