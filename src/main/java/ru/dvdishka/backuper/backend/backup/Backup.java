package ru.dvdishka.backuper.backend.backup;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BaseTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.task.TaskException;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

public interface Backup {

    Storage getStorage();

    default BackupDeleteTask getDeleteTask() {
        return new BackupDeleteTask(this);
    }

    Task getRawDeleteTask();

    LocalDateTime getLocalDateTime();

    String getName();

    default String getFormattedName() {
        return getLocalDateTime().format(Config.getInstance().getDateTimeFormatter());
    }

    long calculateByteSize();

    default long getByteSize() {
        return Backuper.getInstance().getBackupManager().cachedBackupsSize.get(getStorage()).get(this.getName(), (key) -> calculateByteSize());
    }

    default long getMbSize() {
        return getByteSize() / 1024 / 1024;
    }

    BackupFileType getFileType();

    String getFileName();

    String getPath();

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
                Backuper.getInstance().getBackupManager().cachedBackupsSize.get(backup.getStorage()).invalidate(backup.getName());
            }
        }

        @Override
        public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

            if (cancelled) {
                return;
            }
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
}
