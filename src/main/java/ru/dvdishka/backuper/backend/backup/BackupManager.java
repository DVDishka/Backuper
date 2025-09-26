package ru.dvdishka.backuper.backend.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class BackupManager {

    private final Storage storage;

    private final HashMap<String, Backup> backups = new HashMap<>();
    final Cache<String, Long> cachedBackupsSize = Caffeine.newBuilder().build();
    private final Cache<String, List<Backup>> backupList = Caffeine
            .newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .expireAfterAccess(5, TimeUnit.SECONDS)
            .build();

    public BackupManager(Storage storage) {
        this.storage = storage;
    }

    /***
     * @return Backup instance or null if backup doesn't exist
     */
    public Backup getBackup(String backupName) {
        if (!checkBackupExists(backupName)) {
            return null;
        }

        return switch (storage) {
            case LocalStorage localStorage -> backups.computeIfAbsent(backupName, (name) -> new LocalBackup(localStorage, name));
            case FtpStorage ftpStorage -> backups.computeIfAbsent(backupName, (name) -> new FtpBackup(ftpStorage, name));
            case SftpStorage sftpStorage -> backups.computeIfAbsent(backupName, (name) -> new SftpBackup(sftpStorage, name));
            case GoogleDriveStorage googleDriveStorage -> backups.computeIfAbsent(backupName, (name) -> new GoogleDriveBackup(googleDriveStorage, name));
            default -> null;
        };
    }

    public List<Backup> getBackupList() throws Storage.StorageConnectionException, Storage.StorageMethodException {

        return backupList.get("all", (key) -> {
            List<Backup> backups = new ArrayList<>();
            for (String fileName : storage.ls(storage.getConfig().getBackupsFolder())) {
                Backup backup = getBackup(fileName.replace(".zip", ""));
                if (backup != null) {
                    backups.add(backup);
                }
            }
            return backups;
        });
    }

    public boolean checkBackupExists(String backupName) {
        try {
            LocalDateTime.parse(backupName, Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter());
            return storage.ls(storage.getConfig().getBackupsFolder()).stream().anyMatch(file -> file.equals(backupName) || file.equals("%s.zip".formatted(backupName)));
        } catch (Exception e) {
            return false;
        }
    }

    public void saveBackupSizeToCache(String backupName, long byteSize) {
        cachedBackupsSize.put(backupName, byteSize);

        if (StorageType.GOOGLE_DRIVE.equals(storage.getType())) {
            GoogleDriveBackup backup = (GoogleDriveBackup) getBackup(backupName);
            if (backup == null) throw new RuntimeException("Tried to save nonexistent backup's size to cache");
            backup.saveSizeToFileProperties(byteSize);
        }
    }

    public void invalidateBackupSizeCache(String backupName) {
        cachedBackupsSize.invalidate(backupName);
    }

    public ConcurrentMap<String, Long> getSizeCache() {
        return cachedBackupsSize.asMap();
    }

    /***
     * Invalidates the current cache and rewrites it with the provided cache
     */
    public void loadSizeCache(Map<String, Long> cache) {
        cachedBackupsSize.invalidateAll();
        cachedBackupsSize.putAll(cache);
    }
}
