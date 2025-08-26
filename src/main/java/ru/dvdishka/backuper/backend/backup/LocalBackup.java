package ru.dvdishka.backuper.backend.backup;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.*;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;

public class LocalBackup implements Backup {

    private final String backupName;
    private final Storage storage;
    private final LocalDateTime backupLocalDateTime;

    LocalBackup(Storage storage, String backupName) {
        this.backupName = backupName;
        this.storage = storage;
        this.backupLocalDateTime = LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
    }

    public String getName() {
        return backupName;
    }

    public LocalDateTime getLocalDateTime() {
        return backupLocalDateTime;
    }

    public long calculateByteSize() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        String backupFilePath;

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath();
        } else {
            backupFilePath = "%s.zip".formatted(backupsFolder.toPath().resolve(backupName).toFile().getPath());
        }

        long size = Utils.getFileFolderByteSize(new File(backupFilePath));
        return size;
    }

    public BackupFileType getFileType() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        return backupsFolder.toPath().resolve(backupName).toFile().exists() ? BackupFileType.ZIP : BackupFileType.DIR;
    }

    public String getFileName() {
        if (BackupFileType.ZIP.equals(this.getFileType())) {
            return "%s.zip".formatted(backupName);
        } else {
            return backupName;
        }
    }

    public String getPath() {
        return new File(Config.getInstance().getLocalConfig().getBackupsFolder(), getFileName()).getPath();
    }

    public File getFile() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        if (BackupFileType.ZIP.equals(this.getFileType())) {
            return backupsFolder.toPath().resolve("%s.zip".formatted(backupName)).toFile();
        } else {
            return backupsFolder.toPath().resolve(backupName).toFile();
        }
    }

    public File getZIPFile() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        if (backupsFolder.toPath().resolve("%s.zip".formatted(backupName)).toFile().exists()) {
            return backupsFolder.toPath().resolve("%s.zip".formatted(backupName)).toFile();
        }
        return null;
    }

    @Override
    public Storage getStorage() {
        return storage;
    }

    @Override
    public Task getRawDeleteTask() {
        return new LocalDeleteDirTask(this.getFile());
    }

    public Task getRawToZipTask() {
        return new ConvertFolderToZipTask(this.getFile());
    }

    public Task getRawUnZipTask() {
        return new ConvertZipToFolderTask(this.getZIPFile());
    }

    public BackupToZipTask getToZipTask() {
        return new BackupToZipTask(this);
    }

    public BackupUnZipTask getUnZipTask() {
        return new BackupUnZipTask(this);
    }

    public static class BackupUnZipTask extends BaseTask {

        private final LocalBackup backup;
        private Task unZipTask;

        public BackupUnZipTask(LocalBackup backup) {
            super();
            this.backup = backup;
        }

        @Override
        public void run() {
            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(unZipTask, sender);
                } catch (TaskException e) {
                    warn(e);
                }
                Backuper.getInstance().getBackupManager().cachedBackupsSize.get(backup.getStorage()).invalidate(backup.getName());
            }
        }

        @Override
        public void prepareTask(CommandSender sender) {

            if (cancelled) {
                return;
            }

            unZipTask = backup.getRawUnZipTask();
            Backuper.getInstance().getTaskManager().cancelTaskRaw(unZipTask);
        }

        @Override
        public void cancel() {
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

    public static class BackupToZipTask extends BaseTask {

        private final LocalBackup backup;
        private Task toZipTask;

        public BackupToZipTask(LocalBackup backup) {
            super();
            this.backup = backup;
        }

        @Override
        public void run() {
            if (!cancelled) {
                try {
                    Backuper.getInstance().getTaskManager().startTaskRaw(toZipTask, sender);
                } catch (Exception e) {
                    warn(new TaskException(toZipTask, e));
                }
                Backuper.getInstance().getBackupManager().cachedBackupsSize.get(backup.getStorage()).invalidate(backup.getName());
            }
        }

        @Override
        public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

            if (cancelled) {
                return;
            }

            toZipTask = backup.getRawToZipTask();
            Backuper.getInstance().getTaskManager().prepareTask(toZipTask, sender);
        }

        @Override
        public void cancel() {
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
}
