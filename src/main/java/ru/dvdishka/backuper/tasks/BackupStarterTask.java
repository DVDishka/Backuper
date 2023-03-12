package ru.dvdishka.backuper.tasks;

import org.bukkit.Bukkit;
import org.bukkit.World;
import ru.dvdishka.backuper.common.CommonVariables;

public class BackupStarterTask implements Runnable {

    // IF YOU NEED TO BACKUP WITHOUT STOP OR RESTART DESPITE THE CONFIGS
    private final boolean stopRestartServer;

    public BackupStarterTask() {

        this.stopRestartServer = true;
    }

    public BackupStarterTask(boolean stopRestartServer) {

        this.stopRestartServer = stopRestartServer;
    }

    public void run() {

        try {

            CommonVariables.logger.info("Backup process has been started!");

            for (World world : Bukkit.getWorlds()) {

                if (!world.getWorldFolder().setReadOnly()) {

                    CommonVariables.logger.warning("Can not set " + world.getWorldFolder().getPath() + " read only!");
                }
            }

            Bukkit.getScheduler().runTaskAsynchronously(CommonVariables.plugin, new BackuperAsyncTask(stopRestartServer));

        } catch (Exception e) {

            for (World world : Bukkit.getWorlds()) {

                if (!world.getWorldFolder().setWritable(true)) {

                    CommonVariables.logger.warning("Can not set " + world.getWorldFolder().getPath() + " writable!");
                }
            }

            CommonVariables.logger.warning("Backup process has been finished with an exception!");
            CommonVariables.logger.warning(e.getMessage());
        }
    }
}