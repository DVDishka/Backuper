package ru.dvdishka.backuper.backend.classes;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class LocalBackup implements Backup {

    private String backupName;
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
            Logger.getLogger().warn("Wrong local.backupsFolder config value! (Maybe the specified folder does not exist)");
            return backups;
        }

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

    public String getFileName() {
        if (getFileType().equals("(ZIP)")) {
            return backupName + ".zip";
        }
        else {
            return backupName;
        }
    }

    public String getPath() {
        return new File(Config.getInstance().getLocalConfig().getBackupsFolder(), getFileName()).getPath();
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
            LocalDateTime.parse(backupName, Config.getInstance().getDateTimeFormatter());
        } catch (Exception e) {
            return false;
        }

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

        return backupsFolder.toPath().resolve(backupName).toFile().exists() ||
                backupsFolder.toPath().resolve(backupName + ".zip").toFile().exists();
    }

    public void delete(boolean setLocked, CommandSender sender) {
        getDeleteTask(setLocked, sender).run();
    }

    public Task getDeleteTask(boolean setLocked, CommandSender sender) {
        return new DeleteDirTask(this.getFile(), setLocked, List.of(Permissions.LOCAL_DELETE), sender);
    }
}
