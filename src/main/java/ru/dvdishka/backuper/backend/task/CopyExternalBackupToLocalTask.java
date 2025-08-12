package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.ExternalBackup;
import ru.dvdishka.backuper.backend.config.Config;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class CopyExternalBackupToLocalTask extends BaseAsyncTask {

    private final ExternalBackup backup;
    private BaseTask copyToLocalTask = null;

    public CopyExternalBackupToLocalTask(ExternalBackup backup) {
        super();
        this.backup = backup;
    }

    @Override
    protected void run() {

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(copyToLocalTask, sender);
            } catch (TaskException e) {
                warn(e);
            }
        }

        if (!cancelled) {

            String inProgressName = backup.getName() + " in progress";
            if (Backup.BackupFileType.ZIP.equals(backup.getFileType())) {
                inProgressName += ".zip";
            }
            File inProgressFile = new File(Config.getInstance().getLocalConfig().getBackupsFolder(), inProgressName);
            final String backupFileName = backup.getFileName();

            if (!inProgressFile.renameTo(new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName))) {
                warn("Failed to rename local file: \"%s\" to \"%s\"".formatted(inProgressFile.getAbsolutePath(), new File(Config.getInstance().getLocalConfig().getBackupsFolder(), backupFileName).getAbsolutePath()), sender);
            }
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

        if (cancelled) {
            return;
        }

        copyToLocalTask = backup.getRawCopyToLocalTask();
        Backuper.getInstance().getTaskManager().prepareTask(copyToLocalTask, sender);
    }

    @Override
    protected void cancel() {
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
