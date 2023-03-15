package ru.dvdishka.backuper.handlers.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.dvdishka.backuper.common.CommandInterface;
import ru.dvdishka.backuper.common.CommonVariables;
import ru.dvdishka.backuper.tasks.BackupStarterTask;

public class Backup implements CommandInterface {

    private String afterBackup = "NOTHING";

    public Backup() {}

    public Backup(String afterBackup) {

        this.afterBackup = afterBackup;
    }

    @Override
    public void execute(CommandSender sender, Object[] args) {

        Bukkit.getScheduler().runTask(CommonVariables.plugin, new BackupStarterTask(afterBackup));

        returnSuccess("Backup process has been started!\nYou can see the result in the console", sender);
    }
}
