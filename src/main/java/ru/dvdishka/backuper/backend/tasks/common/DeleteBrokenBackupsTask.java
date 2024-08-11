package ru.dvdishka.backuper.backend.tasks.common;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.ftp.FtpDeleteDirTask;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.tasks.sftp.SftpDeleteDirTask;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.SftpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeleteBrokenBackupsTask extends Task {

    private static final String taskName = "DeleteBrokenBackupsTask";

    private ArrayList<Task> tasks = new ArrayList<>();

    public DeleteBrokenBackupsTask(boolean setLocked, List<Permissions> permission, CommandSender sender) {
        super(taskName, setLocked, permission, sender);
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {

            Logger.getLogger().devLog("DeleteBrokenBackups task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            for (Task task : tasks) {
                if (cancelled) {
                    break;
                }
                task.run();
            }

            if (setLocked) {
                Logger.getLogger().log("DeleteBrokenBackups task completed");
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().devLog("DeleteBrokenBackups task has been finished");

        } catch (Exception e) {

            Logger.getLogger().warn("Something went wrong when trying to delete broken backups", sender);
            Logger.getLogger().warn(this, e);

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }
        }
    }

    @Override
    public void prepareTask() {

        if (cancelled) {
            return;
        }

        if (Config.getInstance().getLocalConfig().isEnabled()) {

            File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());

            if (!backupsFolder.exists() || backupsFolder.listFiles() == null) {
                Logger.getLogger().warn("Wrong local backupsFolder!");
            } else {

                for (File file : backupsFolder.listFiles()) {

                    if (cancelled) {
                        return;
                    }

                    if (file.getName().replace(".zip", "").endsWith(" in progress")) {
                        tasks.add(new DeleteDirTask(file, false, permissions, sender));
                    }
                }
            }
        }

        if (Config.getInstance().getFtpConfig().isEnabled()) {
            if (FtpUtils.checkConnection(sender)) {

                ArrayList<String> files = FtpUtils.ls(Config.getInstance().getFtpConfig().getBackupsFolder(), sender);

                for (String file : files) {

                    if (cancelled) {
                        return;
                    }

                    if (file.replace(".zip", "").endsWith(" in progress")) {
                        tasks.add(new FtpDeleteDirTask(FtpUtils.resolve(Config.getInstance().getFtpConfig().getBackupsFolder(), file), false, permissions, sender));
                    }
                }

            } else {
                Logger.getLogger().warn("Failed to establish FTP(S) connection");
            }
        }

        if (Config.getInstance().getSftpConfig().isEnabled()) {
            if (SftpUtils.checkConnection(sender)) {

                ArrayList<String> files = SftpUtils.ls(Config.getInstance().getSftpConfig().getBackupsFolder(), sender);

                for (String file : files) {

                    if (cancelled) {
                        return;
                    }

                    if (file.replace(".zip", "").endsWith(" in progress")) {
                        tasks.add(new SftpDeleteDirTask(FtpUtils.resolve(Config.getInstance().getSftpConfig().getBackupsFolder(), file), false, permissions, sender));
                    }
                }

            } else {
                Logger.getLogger().warn("Failed to establish SFTP connection");
            }
        }
    }

    @Override
    public void cancel() {

        cancelled = true;

        for (Task task : tasks) {
            task.cancel();
        }
    }

    @Override
    public long getTaskMaxProgress() {
        long maxProgress = 0;

        for (Task task : tasks) {
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

        for (Task task : tasks) {
            currentProgress += task.getTaskCurrentProgress();
        }

        return currentProgress;
    }
}
