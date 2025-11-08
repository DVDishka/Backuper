package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.task.BackupDeleteTask;
import ru.dvdishka.backuper.backend.task.BackupToZipTask;
import ru.dvdishka.backuper.backend.task.BackupUnZipTask;

import java.time.LocalDateTime;

public interface Backup extends Comparable<Backup> {

    Storage getStorage();

    default LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(getName(), Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter());
    }
    String getName();

    default String getFormattedName() {
        return getLocalDateTime().format(Backuper.getInstance().getConfigManager().getBackupConfig().getDateTimeFormatter());
    }

    default long calculateByteSize() {
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

    default String getFileName(BackupFileType fileType) {
        if (BackupFileType.ZIP.equals(fileType)) {
            return "%s.zip".formatted(getName());
        } else {
            return getName();
        }
    }

    default String getFileName() {
        return getFileName(getFileType());
    }

    default String getInProgressFileName(BackupFileType fileType) {
        if (BackupFileType.ZIP.equals(fileType)) {
            return "%s in progress.zip".formatted(getName());
        } else {
            return "%s in progress".formatted(getName());
        }
    }

    default String getInProgressFileName() {
        return getInProgressFileName(getFileType());
    }

    default String getPath(BackupFileType fileType) {
        return getStorage().resolve(getStorage().getConfig().getBackupsFolder(), getFileName(fileType));
    }

    default String getPath() {
        return getPath(getFileType());
    }

    default String getInProgressPath(BackupFileType fileType) {
        return getStorage().resolve(getStorage().getConfig().getBackupsFolder(), getInProgressFileName(fileType));
    }

    default String getInProgressPath() {
        return getInProgressPath(getFileType());
    }

    default BackupDeleteTask getDeleteTask() {
        return new BackupDeleteTask(this);
    }

    default BackupUnZipTask getUnZipTask() {
        return new BackupUnZipTask(this);
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

    enum BackupFileType {
        DIR,
        ZIP
    }
}
