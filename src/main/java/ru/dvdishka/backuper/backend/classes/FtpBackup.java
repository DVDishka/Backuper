package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpDeleteDirTask;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.SftpUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FtpBackup implements Backup {

    private String backupName;

    private static HashMap<String, FtpBackup> backups = new HashMap<>();
    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm-ss");

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
            LocalDateTime.parse(backupName, dateTimeFormatter);
        } catch (Exception e) {
            return false;
        }

        List<String> backupFileNames = FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder(), null);

        return backupFileNames.contains(backupName) || backupFileNames.contains(backupName + ".zip");
    }

    public static ArrayList<FtpBackup> getBackups() {

        if (!Config.getInstance().getFtpConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<FtpBackup> backups = new ArrayList<>();
        for (String fileName : FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder(), null)) {
            try {
                FtpBackup backup = FtpBackup.getInstance(fileName.replace(".zip", ""));

                if (backup != null) {
                    backups.add(backup);
                }
            } catch (Exception ignored) {}
        }
        return backups;
    }

    public void delete(boolean setLocked, CommandSender sender) {
        getDeleteTask(setLocked, sender).run();
    }

    public Task getDeleteTask(boolean setLocked, CommandSender sender) {
        return new FtpDeleteDirTask(getPath(), setLocked, sender);
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(backupName, dateTimeFormatter);
    }

    public String getName() {
        return backupName;
    }

    public String getFileName() {
        if (getFileType().equals("(ZIP)")) {
            return backupName + ".zip";
        }
        else {
            return backupName;
        }
    }

    public long getByteSize(CommandSender sender) {
        return FtpUtils.getDirByteSize(getPath(), sender);
    }

    public long getMbSize(CommandSender sender) {
        return getByteSize(sender) / 1024 / 1024;
    }

    public String getFileType() {
        if (FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder(), null).contains(backupName + ".zip")) {
            return "(ZIP)";
        }
        return "(Folder)";
    }

    public String getPath() {
        return FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), getFileName());
    }
}
