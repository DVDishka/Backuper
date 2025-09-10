package ru.dvdishka.backuper.backend;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.FtpStorage;
import ru.dvdishka.backuper.backend.storage.GoogleDriveStorage;
import ru.dvdishka.backuper.backend.storage.LocalStorage;
import ru.dvdishka.backuper.backend.storage.SftpStorage;

public class Bstats {

    private Metrics bStats;
    private final int bStatsId = 17735;

    public void init(JavaPlugin plugin) {

        Backuper.getInstance().getLogManager().log("Initializing BStats...");
        bStats = new Metrics(plugin, bStatsId);

        bStats.addCustomChart(new SimplePie("local_storages_amount", () -> String.valueOf(Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage instanceof LocalStorage && storage.getConfig().isEnabled()).count())));
        bStats.addCustomChart(new SimplePie("ftp_storages_amount", () -> String.valueOf(Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage instanceof FtpStorage && storage.getConfig().isEnabled()).count())));
        bStats.addCustomChart(new SimplePie("sftp_storages_amount", () -> String.valueOf(Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage instanceof SftpStorage && storage.getConfig().isEnabled()).count())));
        bStats.addCustomChart(new SimplePie("google_drive_storages_amount", () -> String.valueOf(Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage instanceof GoogleDriveStorage && storage.getConfig().isEnabled()).count())));

        Backuper.getInstance().getLogManager().log("BStats initialization completed");
    }

    public void destroy() {
        bStats.shutdown();
    }
}
