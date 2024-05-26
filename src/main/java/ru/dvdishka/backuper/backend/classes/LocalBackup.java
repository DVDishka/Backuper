package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public class LocalBackup implements Backup {

    private String backupName;
    private LocalDateTime backupLocalDateTime;

    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

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
        this.backupLocalDateTime = LocalDateTime.parse(backupName, dateTimeFormatter);
    }

    public static ArrayList<LocalBackup> getBackups() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            return new ArrayList<>();
        }

        ArrayList<LocalBackup> backups = new ArrayList<>();
        for (File file : Objects.requireNonNull(new File(Config.getInstance().getLocalConfig().getBackupsFolder()).listFiles())) {
            try {
                LocalBackup localBackup = LocalBackup.getInstance(file.getName().replace(".zip", ""));

                if (localBackup != null) {
                    backups.add(localBackup);
                }
            } catch (Exception ignored) {}
        }
        return backups;
    }

    public String getName() {
        return backupName;
    }

    public LocalDateTime getLocalDateTime() {
        return backupLocalDateTime;
    }

    public long getMbSize(CommandSender sender) {

        return getByteSize(sender) / 1024 / 1024;
    }

    public long getByteSize(CommandSender sender) {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        String backupFilePath;

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath();
        } else {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath() + ".zip";
        }

        return Utils.getFileFolderByteSize(new File(backupFilePath));
    }

    public String getFileType() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        String zipOrFolder = "(ZIP)";

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            zipOrFolder = "(Folder)";
        }

        return zipOrFolder;
    }

    public File getFile() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        if (this.getFileType().equals("(ZIP)")) {
            return backupsFolder.toPath().resolve(backupName + ".zip").toFile();
        } else {
            return backupsFolder.toPath().resolve(backupName).toFile();
        }
    }

    public File getZIPFile() {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        if (backupsFolder.toPath().resolve(backupName + ".zip").toFile().exists()) {
            return backupsFolder.toPath().resolve(backupName + ".zip").toFile();
        }
        return null;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
    public static boolean checkBackupExistenceByName(String backupName) {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            return false;
        }

        try {
            LocalDateTime.parse(backupName, dateTimeFormatter);
        } catch (Exception e) {
            return false;
        }

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        return backupsFolder.toPath().resolve(backupName).toFile().exists() ||
                backupsFolder.toPath().resolve(backupName + ".zip").toFile().exists();
    }

    public void delete(boolean setLocked, CommandSender sender) {

        new DeleteDirTask(this.getFile(), setLocked, sender).run();
    }
}
