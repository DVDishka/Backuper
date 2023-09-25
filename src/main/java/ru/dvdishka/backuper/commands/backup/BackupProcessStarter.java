package ru.dvdishka.backuper.commands.backup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.Scheduler;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.Logger;

import java.util.HashMap;

public class BackupProcessStarter implements Runnable {

    private final String afterRestart;
    private CommandSender sender = null;
    private boolean isAutoBackup = false;
    public static HashMap<String, Boolean> isAutoSaveEnabled = new HashMap<>();

    @SuppressWarnings("unused")
    public BackupProcessStarter(String afterRestart) {

        this.afterRestart = afterRestart;
    }

    public BackupProcessStarter(String afterRestart, boolean isAutoBackup) {

        this.afterRestart = afterRestart;
        this.isAutoBackup = isAutoBackup;
    }

    public BackupProcessStarter(String afterRestart, CommandSender sender) {

        this.afterRestart = afterRestart;
        this.sender = sender;
    }

    public void run() {

        try {

            Logger.getLogger().log("Backup process has been started!", sender);

            Backup.isBackupBusy = true;

            for (World world : Bukkit.getWorlds()) {

                isAutoSaveEnabled.put(world.getName(), world.isAutoSave());

                world.setAutoSave(false);
                if (!world.getWorldFolder().setReadOnly()) {
                    Logger.getLogger().warn("Can not set folder read only!", sender);
                }
            }

            Scheduler.getScheduler().runAsync(Common.plugin, new BackupProcess(afterRestart, isAutoBackup, sender));

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