package ru.dvdishka.backuper.backend.backup;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.*;

import java.time.LocalDateTime;
import java.util.List;

public interface Backup extends Comparable<Backup> {

    Storage getStorage();

    default LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(getName(), Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter());
    }
    String getName();

    default String getFormattedName() {
        return getLocalDateTime().format(Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter());
    }

    private long calculateByteSize() {
        return getStorage().getDirByteSize(getPath());
    }

    default long getByteSize() {
        return getStorage().getBackupManager().cachedBackupsSize.get(this.getName(), (key) -> calculateByteSize());
    }

    default long getMbSize() {
        return getByteSize() / 1024 / 1024;
    }

    default BackupFileType getFileType() {
        if (getStorage().ls(getStorage().getConfig().getBackupsFolder()).contains("%s.zip".formatted(getName()))) {
            return BackupFileType.ZIP;
        }
        return BackupFileType.DIR;
    }

    default String getFileName() {
        if (BackupFileType.ZIP.equals(getFileType())) {
            return "%s.zip".formatted(getName());
        } else {
            return getName();
        }
    }

    default String getPath() {
        return getStorage().resolve(getStorage().getConfig().getBackupsFolder(), getFileName());
    }

    private DeleteDirTask getRawDeleteTask() {
        return new DeleteDirTask(getStorage(), getPath());
    }

    default BackupDeleteTask getDeleteTask() {
        return new BackupDeleteTask(this);
    }

    private UnpackZipTask getRawUnZipTask() {
        return new UnpackZipTask(getStorage(), getPath(), getStorage().resolve(getStorage().getConfig().getBackupsFolder(), getName()));
    }

    default BackupUnZipTask getUnZipTask() {
        return new BackupUnZipTask(this);
    }

    private TransferDirsAsZipTask getRawToZipTask() {
        return new TransferDirsAsZipTask(getStorage(), List.of(getPath()), getStorage(), getStorage().getConfig().getBackupsFolder(), "%s.zip".formatted(getName()), false, false);
    }

    default BackupToZipTask getToZipTask() {
        return new BackupToZipTask(this);
    }

    /***
     * Older is greater
     */
    @Override
    default int compareTo(Backup backup) {
        return this.getLocalDateTime().compareTo(backup.getLocalDateTime());
    }

    class BackupDeleteTask extends BaseTask {

        private final Backup backup;
        private Task deleteBackupTask;

        public BackupDeleteTask(Backup backup) {
            super();
            this.backup = backup;
        }

        @Override
        public void run() {

            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(deleteBackupTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(deleteBackupTask, e));
                }
                backup.getStorage().getBackupManager().cachedBackupsSize.invalidate(backup.getName());
            }
        }

        @Override
        public void prepareTask(CommandSender sender) throws Throwable {
            if (cancelled) return;
            deleteBackupTask = backup.getRawDeleteTask();
            Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (deleteBackupTask != null) {
                Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteBackupTask);
            }
        }

        @Override
        public long getTaskMaxProgress() {
            if (!isTaskPrepared()) return 0;
            return deleteBackupTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {
            if (!isTaskPrepared()) return 0;
            return deleteBackupTask.getTaskCurrentProgress();
        }
    }

    enum StorageType {
        LOCAL,
        FTP,
        SFTP,
        GOOGLE_DRIVE,
        NULL
    }

    enum BackupFileType {
        DIR,
        ZIP
    }

    class BackupUnZipTask extends BaseTask {

        private final Backup backup;
        private UnpackZipTask unZipTask;
        private DeleteDirTask deleteZipTask;

        public BackupUnZipTask(Backup backup) {
            super();
            this.backup = backup;
        }

        @Override
        public void run() {
            try {
                if (!cancelled) Backuper.getInstance().getTaskManager().startTaskRaw(unZipTask, sender);
                if (!cancelled) Backuper.getInstance().getTaskManager().startTaskRaw(deleteZipTask, sender);
            } catch (TaskException e) {
                warn(e);
            }
            backup.getStorage().getBackupManager().cachedBackupsSize.invalidate(backup.getName());
        }

        @Override
        public void prepareTask(CommandSender sender) throws Throwable {
            if (cancelled) return;
            deleteZipTask = backup.getRawDeleteTask();
            Backuper.getInstance().getTaskManager().prepareTask(deleteZipTask, sender);
            unZipTask = backup.getRawUnZipTask();
            Backuper.getInstance().getTaskManager().prepareTask(unZipTask, sender);
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (unZipTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(unZipTask);
            if (deleteZipTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteZipTask);
        }

        @Override
        public long getTaskMaxProgress() {
            if (!isTaskPrepared()) return 0;
            return unZipTask.getTaskMaxProgress() + deleteZipTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {
            if (!isTaskPrepared()) return 0;
            return unZipTask.getTaskCurrentProgress() + deleteZipTask.getTaskCurrentProgress();
        }
    }

    class BackupToZipTask extends BaseTask {

        private final Backup backup;
        private TransferDirsAsZipTask toZipTask;
        private DeleteDirTask deleteFolderTask;

        public BackupToZipTask(Backup backup) {
            super();
            this.backup = backup;
        }

        @Override
        public void run() {
            try {
                if (!cancelled) Backuper.getInstance().getTaskManager().startTaskRaw(toZipTask, sender);
                if (!cancelled) Backuper.getInstance().getTaskManager().startTaskRaw(deleteFolderTask, sender);
            } catch (Exception e) {
                warn(new TaskException(toZipTask, e));
            }
            backup.getStorage().getBackupManager().cachedBackupsSize.invalidate(backup.getName());
        }

        @Override
        public void prepareTask(CommandSender sender) throws Throwable {
            if (cancelled) return;
            deleteFolderTask = backup.getRawDeleteTask();
            Backuper.getInstance().getTaskManager().prepareTask(deleteFolderTask, sender);
            toZipTask = backup.getRawToZipTask();
            Backuper.getInstance().getTaskManager().prepareTask(toZipTask, sender);
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (toZipTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(toZipTask);
            if (deleteFolderTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteFolderTask);
        }

        @Override
        public long getTaskMaxProgress() {
            if (!isTaskPrepared()) return 0;
            return toZipTask.getTaskMaxProgress() + deleteFolderTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {
            if (!isTaskPrepared()) return 0;
            return toZipTask.getTaskCurrentProgress() + deleteFolderTask.getTaskCurrentProgress();
        }
    }
}
