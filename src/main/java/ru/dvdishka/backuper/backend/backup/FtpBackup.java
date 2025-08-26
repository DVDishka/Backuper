package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.FtpDeleteDirTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.util.FtpUtils;

import java.io.IOException;
import java.time.LocalDateTime;

public class FtpBackup extends ExternalBackup {

    private final String backupName;

    FtpBackup(String backupName) {
        this.backupName = backupName;
    }

    @Override
    public Task getRawDeleteTask() {
        return new FtpDeleteDirTask(getPath());
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
    }

    public String getName() {
        return backupName;
    }

    public String getFileName() {
        if (BackupFileType.ZIP.equals(getFileType())) {
            return "%s.zip".formatted(backupName);
        } else {
            return backupName;
        }
    }

    long calculateByteSize() {
        long size;
        try {
            size = FtpUtils.getDirByteSize(getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return size;
    }

    public BackupFileType getFileType() {
        try {
            if (FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder()).contains("%s.zip".formatted(backupName))) {
                return BackupFileType.ZIP;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return BackupFileType.DIR;
    }

    public String getPath() {
        return FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), getFileName());
    }
}
