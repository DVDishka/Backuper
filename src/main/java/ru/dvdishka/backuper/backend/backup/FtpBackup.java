package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.FtpDeleteDirTask;
import ru.dvdishka.backuper.backend.util.FtpUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FtpBackup extends ExternalBackup {

    private static final HashMap<String, FtpBackup> backups = new HashMap<>();

    private FtpBackup(String backupName) {
        this.backupName = backupName;
    }

    public static FtpBackup getInstance(String backupName) {

        if (!checkBackupExistenceByName(backupName)) {
            return null;
        }
        if (backups.containsKey(backupName)) {
            return backups.get(backupName);
        }
        FtpBackup backup = new FtpBackup(backupName);
        backups.put(backupName, backup);
        return backup;
    }

    public static boolean checkBackupExistenceByName(String backupName) {

        if (!Config.getInstance().getFtpConfig().isEnabled()) {
            return false;
        }

        try {
            LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
        } catch (Exception e) {
            return false;
        }

        List<String> backupFileNames;
        try {
            backupFileNames = FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return backupFileNames.contains(backupName) || backupFileNames.contains("%s.zip".formatted(backupName));
    }

    public static ArrayList<FtpBackup> getBackups() {

        if (!Config.getInstance().getFtpConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<FtpBackup> backups = new ArrayList<>();
        try {
            for (String fileName : FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder())) {
                try {
                    FtpBackup backup = FtpBackup.getInstance(fileName.replace(".zip", ""));

                    if (backup != null) {
                        backups.add(backup);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return backups;
    }

    @Override
    public BaseAsyncTask getRawDeleteTask() {
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
