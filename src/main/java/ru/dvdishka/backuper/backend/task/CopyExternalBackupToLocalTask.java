package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.ExternalBackup;
import ru.dvdishka.backuper.backend.config.Config;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class CopyExternalBackupToLocalTask extends BaseTask {

    private final ExternalBackup backup;
    private BaseTask copyToLocalTask = null;

    public CopyExternalBackupToLocalTask(ExternalBackup backup) {
        super();
        this.backup = backup;
    }

    @Override
    public void run() {

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(copyToLocalTask, sender);
            } catch (TaskException e) {
                warn(e);
            }
        }

        if (!cancelled) {

            String inProgressName = "%s in progress".formatted(backup.getName());
            if (Backup.BackupFileType.ZIP.equals(backup.getFileType())) {
                inProgressName = "%s.zip".formatted(inProgressName);
            }
            File inProgressFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder(), inProgressName);
            final String backupFileName = backup.getFileName();

            if (!inProgressFile.renameTo(new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName))) {
                warn("Failed to rename local file: \"%s\" to \"%s\"".formatted(inProgressFile.getAbsolutePath(), new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName).getAbsolutePath()), sender);
            }
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

        if (cancelled) {
            return;
        }

        copyToLocalTask = backup.getRawCopyToLocalTask();
        Backuper.getInstance().getTaskManager().prepareTask(copyToLocalTask, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (copyToLocalTask != null) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(copyToLocalTask);
        }
    }

    @Override
    public long getTaskMaxProgress() {

        if (!isTaskPrepared()) {
            return 0;
        }

        return copyToLocalTask.getTaskMaxProgress();
    }

    @Override
    public long getTaskCurrentProgress() {

        if (!isTaskPrepared()) {
            return 0;
        }

        return copyToLocalTask.getTaskCurrentProgress();
    }
}
