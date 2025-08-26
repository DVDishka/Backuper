package ru.dvdishka.backuper.backend.backup;

import com.jcraft.jsch.SftpException;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.SftpDeleteDirTask;
import ru.dvdishka.backuper.backend.task.Task;
import ru.dvdishka.backuper.backend.util.SftpUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SftpBackup extends ExternalBackup {

    private final String backupName;

    SftpBackup(String backupName) {
        this.backupName = backupName;
    }

    public static List<SftpBackup> getBackups() {

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<SftpBackup> backups = new ArrayList<>();
        try {
            for (String fileName : SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder())) {
                try {
                    SftpBackup backup = SftpBackup.getInstance(fileName.replace(".zip", ""));

                    if (backup != null) {
                        backups.add(backup);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
        return backups;
    }

    public String getName() {
        return backupName;
    }

    public String getFileName() {
        if (BackupFileType.ZIP.equals(this.getFileType())) {
            return "%s.zip".formatted(backupName);
        } else {
            return backupName;
        }
    }

    public BackupFileType getFileType() {
        try {
            return SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder()).contains("%s.zip".formatted(backupName)) ? BackupFileType.ZIP : BackupFileType.DIR;
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Task getRawDeleteTask() {
        return new SftpDeleteDirTask(getPath());
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
    }

    long calculateByteSize() {
        long size;
        try {
            size = SftpUtils.getDirByteSize(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), getFileName()));
        } catch (SftpException e) {
            throw new RuntimeException(e);
        }
        return size;
    }

    public String getPath() {
        return SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), getFileName());
    }
}
