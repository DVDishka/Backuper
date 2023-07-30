package ru.dvdishka.backuper.tasks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.classes.Logger;
import ru.dvdishka.backuper.common.classes.Scheduler;

public class BackupStarterTask implements Runnable {

    private String afterRestart = "NOTHING";
    private boolean isAutoBackup = false;

    public BackupStarterTask(String afterRestart) {

        this.afterRestart = afterRestart;
    }

    public BackupStarterTask(String afterRestart, boolean isAutoBackup) {

        this.afterRestart = afterRestart;
        this.isAutoBackup = isAutoBackup;
    }

    public void run() {

        try {

            Logger.getLogger().log("Backup process has been started!");

            for (World world : Bukkit.getWorlds()) {
                if (!world.getWorldFolder().setReadOnly()) {
                    Logger.getLogger().devWarn(this, "Can not set folder read only!");
                }
            }

            Scheduler.getScheduler().runAsync(Common.plugin, new BackuperAsyncTask(afterRestart, isAutoBackup));

        } catch (Exception e) {

            for (World world : Bukkit.getWorlds()) {

                if (!world.getWorldFolder().setWritable(true)) {

                    Logger.getLogger().devWarn(this, "Can not set " + world.getWorldFolder().getPath() + " writable!");
                }
            }

            Logger.getLogger().warn("Backup process has been finished with an exception!");
            Logger.getLogger().devWarn(this, e.getStackTrace().toString());
        }
    }
}