package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;

public class BackupDeleteTask extends BaseTask {

    private final Backup backup;
    private Task deleteBackupTask;

    public BackupDeleteTask(Backup backup) {
        super();
        this.backup = backup;
    }

    @Override
    public void run() {
        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(deleteBackupTask, sender);
            } catch (Exception e) {
                warn(new TaskException(deleteBackupTask, e));
            }
            backup.getStorage().getBackupManager().invalidateBackupSizeCache(backup.getName());
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws Throwable {
        if (cancelled) return;
        deleteBackupTask = new DeleteDirTask(backup.getStorage(), backup.getPath());
        Backuper.getInstance().getTaskManager().prepareTask(deleteBackupTask, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (deleteBackupTask != null) {
            Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteBackupTask);
        }
    }

    @Override
    public long getTaskMaxProgress() {
        if (!isTaskPrepared()) return 0;
        return deleteBackupTask.getTaskMaxProgress() * backup.getStorage().getDeleteProgressMultiplier();
    }

    @Override
    public long getTaskCurrentProgress() {
        if (!isTaskPrepared()) return 0;
        return deleteBackupTask.getTaskCurrentProgress() * backup.getStorage().getDeleteProgressMultiplier();
    }
}
