package ru.dvdishka.backuper.backend.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.BaseTask;
import ru.dvdishka.backuper.backend.task.TaskException;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public abstract class Backup {

    String backupName;

    public static HashMap<StorageType, Cache<String, Long>> cachedBackupsSize = new HashMap<>();

    private static final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    static {
        cachedBackupsSize.put(StorageType.LOCAL, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.FTP, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.SFTP, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.GOOGLE_DRIVE, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.NULL, Caffeine.newBuilder().build());
    }

    public static String getSizeCacheJson() {

        HashMap<StorageType, ConcurrentMap<String, Long>> jsonedCache = new HashMap<>();

        for (StorageType storageType : StorageType.values()) {
            jsonedCache.put(storageType, cachedBackupsSize.get(storageType).asMap());
        }

        String json = gson.toJson(jsonedCache);
        return json;
    }

    public static void loadSizeCache(String json) {
        if (json.isEmpty()) {
            return;
        }

        Type typeToken = new TypeToken<HashMap<StorageType, HashMap<String, Long>>>() {}.getType();
        HashMap<StorageType, HashMap<String, Long>> jsonedCache = gson.fromJson(json, typeToken);

        for (StorageType storageType : StorageType.values()) {
            for (Map.Entry<String, Long> entry : jsonedCache.get(storageType).entrySet()) {
                cachedBackupsSize.get(storageType).put(entry.getKey(), entry.getValue());
            }
        }
    }

    public StorageType getStorageType() {
        return switch (this) {
            case LocalBackup localBackup -> StorageType.LOCAL;
            case FtpBackup ftpBackup -> StorageType.FTP;
            case SftpBackup sftpBackup -> StorageType.SFTP;
            case GoogleDriveBackup googleDriveBackup -> StorageType.GOOGLE_DRIVE;
            default -> StorageType.NULL;
        };
    }

    public static void saveBackupSizeToCache(StorageType storageType, String backupName, long byteSize) {
        cachedBackupsSize.get(storageType).put(backupName, byteSize);

        if (storageType == StorageType.GOOGLE_DRIVE) {
            GoogleDriveBackup backup = GoogleDriveBackup.getInstance(backupName);
            if (backup == null) throw new RuntimeException("Tried to save nonexistent backup's size to cache");
            backup.saveSizeToFileProperties(byteSize);
        }
    }

    public BackupDeleteTask getDeleteTask() {
        return new BackupDeleteTask(this);
    }

    public abstract BaseAsyncTask getRawDeleteTask();

    public abstract LocalDateTime getLocalDateTime();

    public abstract String getName();

    public String getFormattedName() {
        return getLocalDateTime().format(Config.getInstance().getDateTimeFormatter());
    }

    abstract long calculateByteSize();

    public long getByteSize() {
        return cachedBackupsSize.get(getStorageType()).get(this.getName(), (key) -> calculateByteSize());
    }

    public long getMbSize() {
        return getByteSize() / 1024 / 1024;
    }

    public abstract BackupFileType getFileType();

    public abstract String getFileName();

    public abstract String getPath();

    public static class BackupUnZipTask extends BaseAsyncTask {

        private final LocalBackup backup;
        private BaseTask unZipTask;

        public BackupUnZipTask(LocalBackup backup) {
            super();
            this.backup = backup;
        }

        @Override
        protected void run() {
            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(unZipTask, sender);
                } catch (TaskException e) {
                    warn(e);
                }
                cachedBackupsSize.get(StorageType.LOCAL).invalidate(backup.getName());
            }
        }

        @Override
        protected void prepareTask(CommandSender sender) {

            if (cancelled) {
                return;
            }

            unZipTask = backup.getRawUnZipTask();
            Backuper.getInstance().getTaskManager().cancelTaskRaw(unZipTask);
        }

        @Override
        protected void cancel() {
            cancelled = true;
            if (unZipTask != null) {
                Backuper.getInstance().getTaskManager().cancelTaskRaw(unZipTask);
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared()) {
                return 0;
            }

            return unZipTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared()) {
                return 0;
            }

            return unZipTask.getTaskCurrentProgress();
        }
    }

    public static class BackupDeleteTask extends BaseAsyncTask {

        private final Backup backup;
        private BaseTask deleteBackupTask;

        public BackupDeleteTask(Backup backup) {
            super();
            this.backup = backup;
        }

        @Override
        protected void run() {

            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(deleteBackupTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(deleteBackupTask, e));
                }
                cachedBackupsSize.get(backup.getStorageType()).invalidate(backup.getName());
            }
        }

        @Override
        protected void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

            if (cancelled) {
                return;
            }
            deleteBackupTask = backup.getRawDeleteTask();
            Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);
        }

        @Override
        protected void cancel() {
            cancelled = true;
            if (deleteBackupTask != null) {
                Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteBackupTask);
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared()) {
                return 0;
            }

            return deleteBackupTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared()) {
                return 0;
            }

            return deleteBackupTask.getTaskCurrentProgress();
        }
    }

    public static class BackupToZipTask extends BaseAsyncTask {

        private final LocalBackup backup;
        private BaseTask toZipTask;

        public BackupToZipTask(LocalBackup backup) {
            super();
            this.backup = backup;
        }

        @Override
        protected void run() {
            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(toZipTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(toZipTask, e));
                }
                cachedBackupsSize.get(StorageType.LOCAL).invalidate(backup.getName());
            }
        }

        @Override
        protected void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

            if (cancelled) {
                return;
            }

            toZipTask = backup.getRawToZipTask();
            Backuper.getInstance().getTaskManager().prepareTask(toZipTask, sender);
        }

        @Override
        protected void cancel() {
            cancelled = true;
            if (toZipTask != null) {
                Backuper.getInstance().getTaskManager().cancelTaskRaw(toZipTask);
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared()) {
                return 0;
            }

            return toZipTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared()) {
                return 0;
            }

            return toZipTask.getTaskCurrentProgress();
        }
    }

    public enum StorageType {
        LOCAL,
        FTP,
        SFTP,
        GOOGLE_DRIVE,
        NULL
    }

    public enum BackupFileType {
        DIR,
        ZIP
    }
}
