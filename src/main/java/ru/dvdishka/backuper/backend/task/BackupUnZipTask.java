package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;

public class BackupUnZipTask extends BaseTask {

    private final Backup backup;
    private UnpackZipTask unZipTask;
    private DeleteDirTask deleteZipTask;

    public BackupUnZipTask(Backup backup) {
        super();
        this.backup = backup;
    }

    @Override
    public void run() {
        try {
            if (!cancelled) Backuper.getInstance().getTaskManager().startTaskRaw(unZipTask, sender);
            if (!cancelled) {
                Backuper.getInstance().getTaskManager().startTaskRaw(deleteZipTask, sender);
                backup.getStorage().renameFile(backup.getInProgressPath(Backup.BackupFileType.DIR), backup.getFileName(Backup.BackupFileType.DIR));
            }
        } catch (TaskException e) {
            warn(e);
        }
        backup.getStorage().getBackupManager().invalidateBackupSizeCache(backup.getName());
    }

    @Override
    public void prepareTask(CommandSender sender) throws Throwable {
        if (cancelled) return;
        deleteZipTask = new DeleteDirTask(backup.getStorage(), backup.getPath());
        Backuper.getInstance().getTaskManager().prepareTask(deleteZipTask, sender);
        backup.getStorage().createDir(backup.getInProgressFileName(Backup.BackupFileType.DIR), backup.getStorage().getConfig().getBackupsFolder()); // For ID based filesystems
        unZipTask = new UnpackZipTask(backup.getStorage(), backup.getPath(), backup.getInProgressPath(Backup.BackupFileType.DIR));
        Backuper.getInstance().getTaskManager().prepareTask(unZipTask, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (unZipTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(unZipTask);
        if (deleteZipTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteZipTask);
    }

    @Override
    public long getTaskMaxProgress() {
        if (!isTaskPrepared()) return 0;
        return unZipTask.getTaskMaxProgress() * backup.getStorage().getZipProgressMultiplier() +
                deleteZipTask.getTaskMaxProgress() * backup.getStorage().getDeleteProgressMultiplier();
    }

    @Override
    public long getTaskCurrentProgress() {
        if (!isTaskPrepared()) return 0;
        return unZipTask.getTaskCurrentProgress() * backup.getStorage().getZipProgressMultiplier() +
                deleteZipTask.getTaskCurrentProgress() * backup.getStorage().getDeleteProgressMultiplier();
    }
}
