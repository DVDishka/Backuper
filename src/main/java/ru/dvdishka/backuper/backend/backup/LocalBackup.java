package ru.dvdishka.backuper.backend.backup;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.tasks.local.zip.tozip.ConvertFolderToZipTask;
import ru.dvdishka.backuper.backend.tasks.local.zip.unzip.ConvertZipToFolderTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            Logger.getLogger().warn("Wrong local.backupsFolder config value! (Maybe the specified folder does not exist)");
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

    long calculateByteSize(CommandSender sender) {

        File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
        String backupFilePath;

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath();
        } else {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath() + ".zip";
        }

        long size = Utils.getFileFolderByteSize(new File(backupFilePath));
        return size;
    }

    /**
     * @return Possible values: "(Folder)" "(ZIP)"
     */
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
        } else {
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

    @Override
    Task getDirectDeleteTask(boolean setLocked, CommandSender sender) {
        return new DeleteDirTask(this.getFile(), setLocked, List.of(Permissions.LOCAL_DELETE), sender);
    }

    private Task getDirectToZipTask(boolean setLocked, CommandSender sender) {
        return new ConvertFolderToZipTask(this.getFile(), setLocked, List.of(Permissions.LOCAL_TO_ZIP), sender);
    }

    private Task getDirectUnZipTask(boolean setLocked, CommandSender sender) {
        return new ConvertZipToFolderTask(this.getZIPFile(), setLocked, List.of(Permissions.LOCAL_UNZIP), sender);
    }

    public Task getToZipTask(boolean setLocked, CommandSender sender) {
        return new BackupToZipTask(this, setLocked, sender);
    }

    public void toZip(boolean setLocked, CommandSender sender) {
        getToZipTask(setLocked, sender).run();
    }

    public Task getUnZipTask(boolean setLocked, CommandSender sender) {
        return new BackupUnZipTask(this, setLocked, sender);
    }

    public void unZip(boolean setLocked, CommandSender sender) {
        getUnZipTask(setLocked, sender).run();
    }

    public class BackupToZipTask extends Task {

        private static final String taskName = "BackupToZip";

        private LocalBackup backup = null;
        private Task toZipTask = null;

        /**
         * @param backup
         * @param setLocked
         * @param sender
         */
        public BackupToZipTask(LocalBackup backup, boolean setLocked, CommandSender sender) {
            super(taskName, setLocked, List.of(Permissions.LOCAL_TO_ZIP), sender);
            this.backup = backup;
        }

        @Override
        public void run() {

            if (cancelled) {
                return;
            }

            try {
                if (setLocked) {
                    Backuper.lock(this);
                }

                if (!isTaskPrepared) {
                    prepareTask();
                }

                if (!cancelled) {
                    toZipTask.run();
                }
                cachedBackupsSize.get(StorageType.LOCAL).invalidate(backup.getName());

                if (setLocked) {
                    UIUtils.successSound(sender);
                    Backuper.unlock();
                }

            } catch (Exception e) {
                if (setLocked) {
                    UIUtils.cancelSound(sender);
                    Backuper.unlock();
                }

                Logger.getLogger().warn(taskName + " task has been finished with an exception", sender);
                Logger.getLogger().warn(this.getClass(), e);
            }
        }

        @Override
        public void prepareTask() {

            isTaskPrepared = true;

            if (cancelled) {
                return;
            }

            toZipTask = backup.getDirectToZipTask(false, sender);
            toZipTask.prepareTask();
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (toZipTask != null) {
                toZipTask.cancel();
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return toZipTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return toZipTask.getTaskCurrentProgress();
        }
    }

    public class BackupUnZipTask extends Task {

        private static final String taskName = "BackupUnZip";

        private LocalBackup backup = null;
        private Task unZipTask = null;

        /**
         * @param backup
         * @param setLocked
         * @param sender
         */
        public BackupUnZipTask(LocalBackup backup, boolean setLocked, CommandSender sender) {
            super(taskName, setLocked, List.of(Permissions.LOCAL_UNZIP), sender);
            this.backup = backup;
        }

        @Override
        public void run() {

            if (cancelled) {
                return;
            }

            try {
                if (setLocked) {
                    Backuper.lock(this);
                }

                if (!isTaskPrepared) {
                    prepareTask();
                }

                if (!cancelled) {
                    unZipTask.run();
                }
                cachedBackupsSize.get(StorageType.LOCAL).invalidate(backup.getName());

                if (setLocked) {
                    UIUtils.successSound(sender);
                    Backuper.unlock();
                }

            } catch (Exception e) {
                if (setLocked) {
                    UIUtils.cancelSound(sender);
                    Backuper.unlock();
                }

                Logger.getLogger().warn(taskName + " task has been finished with an exception", sender);
                Logger.getLogger().warn(this.getClass(), e);
            }
        }

        @Override
        public void prepareTask() {

            isTaskPrepared = true;

            if (cancelled) {
                return;
            }

            unZipTask = backup.getDirectUnZipTask(false, sender);
            unZipTask.prepareTask();
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (unZipTask != null) {
                unZipTask.cancel();
            }
        }

        @Override
        public long getTaskMaxProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return unZipTask.getTaskMaxProgress();
        }

        @Override
        public long getTaskCurrentProgress() {

            if (!isTaskPrepared) {
                return 0;
            }

            return unZipTask.getTaskCurrentProgress();
        }
    }
}
