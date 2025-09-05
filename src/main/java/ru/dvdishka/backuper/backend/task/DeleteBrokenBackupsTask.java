package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.ConfigManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DeleteBrokenBackupsTask extends BaseTask {

    private final ArrayList<BaseTask> tasks = new ArrayList<>();

    public DeleteBrokenBackupsTask() {
        super();
    }

    @Override
    public void run() throws IOException {

        for (BaseTask task : tasks) {
            if (cancelled) {
                return;
            }
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(task, sender);
            } catch (TaskException e) {
                warn(e);
            }
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

        if (cancelled) {
            return;
        }

        if (ConfigManager.getInstance().getLocalConfig().isEnabled() && !cancelled) {

            File backupsFolder = new File(ConfigManager.getInstance().getLocalConfig().getBackupsFolder());

            if (!backupsFolder.exists() || backupsFolder.listFiles() == null) {
                warn("Wrong local backupsFolder!");
            } else {

                for (File file : backupsFolder.listFiles()) {

                    if (cancelled) {
                        return;
                    }

                    if (file.getName().replace(".zip", "").endsWith(" in progress")) {
                        BaseTask task = new LocalDeleteDirTask(file);
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                        tasks.add(task);
                    }
                }
            }
        }

        if (ConfigManager.getInstance().getFtpConfig().isEnabled() && !cancelled && FtpUtils.checkConnection(sender)) {

            try {
                ArrayList<String> files = FtpUtils.ls(ConfigManager.getInstance().getFtpConfig().getBackupsFolder());
                for (String file : files) {

                    if (cancelled) {
                        return;
                    }

                    if (file.replace(".zip", "").endsWith(" in progress")) {
                        BaseTask task = new FtpDeleteDirTask(FtpUtils.resolve(ConfigManager.getInstance().getFtpConfig().getBackupsFolder(), file));
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                        tasks.add(task);
                    }
                }
            } catch (Exception e) {
                warn("Failed to check FTP(S) storage for broken backups", sender);
                warn(e);
            }
        }

        if (ConfigManager.getInstance().getSftpConfig().isEnabled() && !cancelled && SftpUtils.checkConnection(sender)) {

            try {
                ArrayList<String> files = SftpUtils.ls(ConfigManager.getInstance().getSftpConfig().getBackupsFolder());
                for (String file : files) {

                    if (cancelled) {
                        return;
                    }

                    if (file.replace(".zip", "").endsWith(" in progress")) {
                        BaseTask task = new SftpDeleteDirTask(FtpUtils.resolve(ConfigManager.getInstance().getSftpConfig().getBackupsFolder(), file));
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                        tasks.add(task);
                    }
                }
            } catch (Exception e) {
                warn("Failed to check SFTP storage for broken backups", sender);
                warn(e);
            }
        }

        if (ConfigManager.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection() && !cancelled) {

            try {
                List<com.google.api.services.drive.model.File> files = GoogleDriveUtils.ls(ConfigManager.getInstance().getGoogleDriveConfig().getBackupsFolderId());
                for (com.google.api.services.drive.model.File file : files) {

                    if (cancelled) {
                        return;
                    }

                    if (file.getName().replace(".zip", "").endsWith(" in progress")) {
                        BaseTask task = new GoogleDriveDeleteDirTask(file.getId());
                        Backuper.getInstance().getTaskManager().prepareTask(task, sender);
                        tasks.add(task);
                    }
                }
            } catch (Exception e) {
                warn("Failed to check Google Drive storage for broken backups", sender);
                warn(e);
            }
        }
    }

    @Override
    public void cancel() {

        cancelled = true;
        for (BaseTask task : tasks) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(task);
        }
    }

    @Override
    public long getTaskMaxProgress() {
        long maxProgress = 0;

        for (BaseTask task : tasks) {
            maxProgress += task.getTaskMaxProgress();
        }

        return maxProgress;
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return getTaskMaxProgress();
        }

        long currentProgress = 0;

        for (BaseTask task : tasks) {
            currentProgress += task.getTaskCurrentProgress();
        }

        return currentProgress;
    }
}
