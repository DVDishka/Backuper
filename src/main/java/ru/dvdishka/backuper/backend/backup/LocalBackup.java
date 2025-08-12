package ru.dvdishka.backuper.backend.backup;

import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.BaseAsyncTask;
import ru.dvdishka.backuper.backend.task.ConvertFolderToZipTask;
import ru.dvdishka.backuper.backend.task.ConvertZipToFolderTask;
import ru.dvdishka.backuper.backend.task.DeleteDirTask;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class LocalBackup extends ExternalBackup {

    private LocalDateTime backupLocalDateTime;

    private static HashMap<String, LocalBackup> backups = new HashMap();

    public static LocalBackup getInstance(String backupName) {

        if (!checkBackupExistenceByName(backupName)) {
            return null;
        }
        if (backups.containsKey(backupName)) {
            return backups.get(backupName);
        }
        LocalBackup backup = new LocalBackup(backupName);
        backups.put(backupName, backup);
        return backup;
    }

    private LocalBackup(String backupName) {

        this.backupName = backupName;
        this.backupLocalDateTime = LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
    }

    public static ArrayList<LocalBackup> getBackups() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<LocalBackup> backups = new ArrayList<>();

        if (!new File(Config.getInstance().getLocalConfig().getBackupsFolder()).exists() ||
                new File(Config.getInstance().getLocalConfig().getBackupsFolder()).listFiles() == null) {
            Backuper.getInstance().getLogManager().warn("Wrong local.backupsFolder config value! (Maybe the specified folder does not exist)");
            return backups;
        }

        for (File file : Objects.requireNonNull(new File(Config.getInstance().getLocalConfig().getBackupsFolder()).listFiles())) {
            try {
                LocalBackup localBackup = LocalBackup.getInstance(file.getName().replace(".zip", ""));

                if (localBackup != null) {
                    backups.add(localBackup);
                }
            } catch (Exception ignored) {
            }
        }
        return backups;
    }

    public String getName() {
        return backupName;
    }

    public LocalDateTime getLocalDateTime() {
        return backupLocalDateTime;
    }

    long calculateByteSize() {

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

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public static boolean checkBackupExistenceByName(String backupName) {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            return false;
        }

        try {
            LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
        } catch (Exception e) {
            return false;
        }

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        return backupsFolder.toPath().resolve(backupName).toFile().exists() ||
                backupsFolder.toPath().resolve("%s.zip".formatted(backupName)).toFile().exists();
    }

    @Override
    public BaseAsyncTask getRawDeleteTask() {
        return new DeleteDirTask(this.getFile());
    }

    public BaseAsyncTask getRawToZipTask() {
        return new ConvertFolderToZipTask(this.getFile());
    }

    public BaseAsyncTask getRawUnZipTask() {
        return new ConvertZipToFolderTask(this.getZIPFile());
    }

    public BackupToZipTask getToZipTask() {
        return new BackupToZipTask(this);
    }

    public BackupUnZipTask getUnZipTask() {
        return new BackupUnZipTask(this);
    }
}
