package ru.dvdishka.backuperspigot;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;

public class BackuperTask implements Runnable {

    @Override
    public void run() {

        CommonVariables.logger.info("Backup process has been started!");

        CommonVariables.isBackuping = true;

        for (Player player : Bukkit.getOnlinePlayers()) {

            player.kickPlayer("Server goes to backup!");
            player.saveData();
        }

        for (World world : Bukkit.getWorlds()) {

            world.save();
        }

        File backupDir = new File("plugins/Backuper/Backups/" +
                LocalDateTime.now().getDayOfMonth() + '.' +
                LocalDateTime.now().getMonthValue() + '.' +
                LocalDateTime.now().getYear() + '-' +
                LocalDateTime.now().getHour() + ':' +
                LocalDateTime.now().getMinute() + ':' +
                LocalDateTime.now().getSecond());

        backupDir.mkdir();

        for (World world : Bukkit.getWorlds()) {

            File worldDir = world.getWorldFolder();

            if (worldDir.listFiles() != null) {

                try {

                    getFilesInDir(backupDir.toPath().resolve(world.getName()).toFile(), worldDir);

                } catch (Exception e) {

                    CommonVariables.logger.warning("Something went wrong when trying to copy files!");
                    CommonVariables.logger.warning(e.getMessage());
                }
            }
        }

        CommonVariables.isBackuping = false;

        CommonVariables.logger.info("Backup process has been finished!");
    }

    public void getFilesInDir(File destDir, File dir) throws IOException {

        if (dir.listFiles() != null) {

            destDir.mkdir();

            for (File file : dir.listFiles()) {

                if (file.isDirectory()) {

                    getFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);

                } else {

                    Files.copy(file.toPath(), destDir.toPath().resolve(file.getName()));
                }
            }
        }
    }
}
