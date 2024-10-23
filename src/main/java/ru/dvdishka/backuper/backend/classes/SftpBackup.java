package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpDeleteDirTask;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SftpBackup implements Backup {

    private String backupName;

    private static HashMap<String, SftpBackup> backups = new HashMap<>();

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

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            return false;
        }

        try {
            LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
        } catch (Exception e) {
            return false;
        }

        ArrayList<String> backupFileNames = SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), null);

        return backupFileNames.contains(backupName) || backupFileNames.contains(backupName + ".zip");
    }

    public static ArrayList<SftpBackup> getBackups() {

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<SftpBackup> backups = new ArrayList<>();
        for (String fileName : SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), null)) {
            try {
                SftpBackup backup = SftpBackup.getInstance(fileName.replace(".zip", ""));

                if (backup != null) {
                    backups.add(backup);
                }
            } catch (Exception ignored) {
            }
        }
        return backups;
    }

    public String getName() {
        return backupName;
    }

    public String getFileName() {
        if (getFileType().equals("(ZIP)")) {
            return backupName + ".zip";
        } else {
            return backupName;
        }
    }

    /**
     * @return Possible values: "(Folder)" "(ZIP)"
     */
    public String getFileType() {
        if (SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), null).contains(backupName + ".zip")) {
            return "(ZIP)";
        }
        return "(Folder)";
    }

    public void delete(boolean setLocked, CommandSender sender) {
        getDeleteTask(setLocked, sender).run();
    }

    public Task getDeleteTask(boolean setLocked, CommandSender sender) {
        return new SftpDeleteDirTask(getPath(), setLocked, List.of(Permissions.SFTP_DELETE), sender);
    }

    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
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
