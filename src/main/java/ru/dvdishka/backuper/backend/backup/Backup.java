package ru.dvdishka.backuper.backend.backup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class Backup {

    String backupName;

    static final HashMap<StorageType, Cache<String, Long>> cachedBackupsSize = new HashMap<>();

    static {
        cachedBackupsSize.put(StorageType.LOCAL, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.FTP, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.SFTP, Caffeine.newBuilder().build());
        cachedBackupsSize.put(StorageType.GOOGLE_DRIVE, Caffeine.newBuilder().build());
    }

    private StorageType getStorageType() {
        if (this instanceof LocalBackup) {
            return StorageType.LOCAL;
        }
        if (this instanceof FtpBackup) {
            return StorageType.FTP;
        }
        if (this instanceof SftpBackup) {
            return StorageType.SFTP;
        }
        if (this instanceof GoogleDriveBackup) {
            return StorageType.GOOGLE_DRIVE;
        }
        return StorageType.NULL;
    }

    public static void addCalculatedBackupSize(StorageType storageType, String backupName, long byteSize) {
        cachedBackupsSize.get(storageType).put(backupName, byteSize);
    }

    public BackupDeleteTask getDeleteTask(boolean setLocked, CommandSender sender) {
        return new BackupDeleteTask(this, setLocked, sender);
    }

    abstract Task getDirectDeleteTask(boolean setLocked, CommandSender sender);

    public void delete(boolean setLocked, CommandSender sender) {
        getDeleteTask(setLocked, sender).run();
    }

    public abstract LocalDateTime getLocalDateTime();

    public abstract String getName();

    public String getFormattedName() {
        return getLocalDateTime().format(Config.getInstance().getDateTimeFormatter());
    }

    abstract long calculateByteSize(CommandSender sender);

    public long getByteSize(CommandSender sender) {
        return cachedBackupsSize.get(getStorageType()).get(this.getName(), (key) -> calculateByteSize(sender));
    }

    public long getMbSize(CommandSender sender) {
        return getByteSize(sender) / 1024 / 1024;
    }

    public abstract String getFileType();

    public abstract String getFileName();

    public abstract String getPath();

    public class BackupDeleteTask extends Task {

        private static final String taskName = "BackupDelete";

        private Backup backup = null;
        private Task deleteBackupTask = null;

        private static List<Permissions> getDeletePermissions(Backup backup) {
            ArrayList<Permissions> permissions = new ArrayList<>();
            if (backup instanceof LocalBackup) {
                permissions.add(Permissions.LOCAL_DELETE);
            }
            if (backup instanceof SftpBackup) {
                permissions.add(Permissions.SFTP_DELETE);
            }
            if (backup instanceof FtpBackup) {
                permissions.add(Permissions.FTP_DELETE);
            }
            if (backup instanceof GoogleDriveBackup) {
                permissions.add(Permissions.GOOGLE_DRIVE_DELETE);
            }
            return permissions;
        }

        public BackupDeleteTask(Backup backup, boolean setLocked, List<Permissions> permissions, CommandSender sender) {
            super(taskName, setLocked, permissions, sender);
            this.backup = backup;
        }

        /**
         *When using a constructor without a parameter, the permissions corresponding to the type of backup storage will be taken
         * @param backup
         * @param setLocked
         * @param sender
         */
        public BackupDeleteTask(Backup backup, boolean setLocked, CommandSender sender) {
            super(taskName, setLocked, getDeletePermissions(backup), sender);
            this.backup = backup;
        }

        @Override
        public void run() {

            if (cancelled) {
                return;
            }

            try {
                if (setLocked) {
                    Backuper.lock(this);
                }

                if (!isTaskPrepared) {
                    prepareTask();
                }

                if (!cancelled) {
                    deleteBackupTask.run();
                }
                cachedBackupsSize.get(getStorageType()).invalidate(backup.getName());

                if (setLocked) {
                    UIUtils.successSound(sender);
                    Backuper.unlock();
                }

            } catch (Exception e) {
                if (setLocked) {
                    UIUtils.cancelSound(sender);
                    Backuper.unlock();
                }

                Logger.getLogger().warn(taskName + " task has been finished with an exception", sender);
                Logger.getLogger().warn(this.getClass(), e);
            }
        }

        @Override
        public void prepareTask() {

            if (cancelled) {
                return;
            }

            deleteBackupTask = backup.getDirectDeleteTask(false, sender);
            deleteBackupTask.prepareTask();

            isTaskPrepared = true;
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (deleteBackupTask != null) {
                deleteBackupTask.cancel();
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return deleteBackupTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return deleteBackupTask.getTaskCurrentProgress();
        }
    }
}
