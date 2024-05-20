package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpDeleteDirTask;
import ru.dvdishka.backuper.backend.utils.SftpUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

public class SftpBackup implements Backup {

    private String backupName;

    private static HashMap<String, SftpBackup> backups = new HashMap<>();
    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private SftpBackup(String backupName) {
        this.backupName = backupName;
    }

    public static SftpBackup getInstance(String backupName) {

        if (!checkBackupExistenceByName(backupName)) {
            return null;
        }
        if (backups.containsKey(backupName)) {
            return backups.get(backupName);
        }
        SftpBackup backup = new SftpBackup(backupName);
        backups.put(backupName, backup);
        return backup;
    }

    public static boolean checkBackupExistenceByName(String backupName) {

        try {
            LocalDateTime.parse(backupName, dateTimeFormatter);
        } catch (Exception e) {
            return false;
        }

        for (String fileName : SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), null)) {
            if (fileName.equals(backupName) || fileName.equals(backupName + ".zip")) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<SftpBackup> getBackups() {

        ArrayList<SftpBackup> backups = new ArrayList<>();
        for (String fileName : SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), null)) {
            try {
                SftpBackup backup = SftpBackup.getInstance(fileName.replace(".zip", ""));

                if (backup != null) {
                    backups.add(backup);
                }
            } catch (Exception ignored) {}
        }
        return backups;
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

    public String getFileType() {
        if (SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), null).contains(backupName + ".zip")) {
            return "(ZIP)";
        }
        return "(Folder)";
    }

    public void delete(boolean setLocked, CommandSender sender) {

        new SftpDeleteDirTask(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), getFileName()), setLocked, sender).run();
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(backupName, dateTimeFormatter);
    }

    public long getByteSize(CommandSender sender) {
        return SftpUtils.getDirByteSize(SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), getFileName()), sender);
    }

    public long getMbSize(CommandSender sender) {
        return getByteSize(sender) / 1024 / 1024;
    }

    public String getPath() {
        return SftpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), getFileName());
    }
}
