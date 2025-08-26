package ru.dvdishka.backuper.backend.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import ru.dvdishka.backuper.backend.storage.Storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BackupManager {

    private final Storage storage;

    private final HashMap<String, Backup> backups = new HashMap<>();
    public final Cache<String, Long> cachedBackupsSize = Caffeine.newBuilder().build();
    private final Cache<String, ArrayList<Backup>> backupList = Caffeine
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

        return switch (storage.getType()) {
            case LOCAL -> backups.computeIfAbsent(backupName, LocalBackup::new);
            case FTP -> backups.computeIfAbsent(backupName, FtpBackup::new);
            case SFTP -> backups.computeIfAbsent(backupName, SftpBackup::new);
            case GOOGLE_DRIVE -> backups.computeIfAbsent(backupName, GoogleDriveBackup::new);
            default -> null;
        };
    }

    public List<Backup> getBackupList() throws Storage.StorageConnectionException, Storage.StorageMethodException {

        return backupList.get("all", (key) -> {

            ArrayList<Backup> backups = new ArrayList<>();
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
            return storage.ls(storage.getConfig().getBackupsFolder()).stream().anyMatch(file -> file.startsWith(backupName));
        } catch (Exception e) {
            return false;
        }
    }

    public void saveBackupSizeToCache(String backupName, long byteSize) {
        cachedBackupsSize.put(backupName, byteSize);

        if (storageType == Backup.StorageType.GOOGLE_DRIVE) {
            GoogleDriveBackup backup = GoogleDriveBackup.getInstance(backupName);
            if (backup == null) throw new RuntimeException("Tried to save nonexistent backup's size to cache");
            backup.saveSizeToFileProperties(byteSize);
        }
    }
}
