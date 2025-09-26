package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;

import java.util.List;

public class BackupToZipTask extends BaseTask {

    private final Backup backup;
    private TransferDirsAsZipTask toZipTask;
    private DeleteDirTask deleteFolderTask;

    public BackupToZipTask(Backup backup) {
        super();
        this.backup = backup;
    }

    @Override
    public void run() {
        try {
            if (!cancelled) Backuper.getInstance().getTaskManager().startTaskRaw(toZipTask, sender);
            if (!cancelled) {
                Backuper.getInstance().getTaskManager().startTaskRaw(deleteFolderTask, sender);
                backup.getStorage().renameFile(backup.getInProgressPath(Backup.BackupFileType.ZIP), backup.getFileName(Backup.BackupFileType.ZIP));
            }
        } catch (Exception e) {
            warn(new TaskException(toZipTask, e));
        }
        backup.getStorage().getBackupManager().invalidateBackupSizeCache(backup.getName());
    }

    @Override
    public void prepareTask(CommandSender sender) throws Throwable {
        if (cancelled) return;
        deleteFolderTask = new DeleteDirTask(backup.getStorage(), backup.getPath());
        Backuper.getInstance().getTaskManager().prepareTask(deleteFolderTask, sender);
        toZipTask = new TransferDirsAsZipTask(backup.getStorage(), List.of(backup.getPath()), backup.getStorage(), backup.getStorage().getConfig().getBackupsFolder(), backup.getInProgressFileName(Backup.BackupFileType.ZIP), false, true);
        Backuper.getInstance().getTaskManager().prepareTask(toZipTask, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (toZipTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(toZipTask);
        if (deleteFolderTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteFolderTask);
    }

    @Override
    public long getTaskMaxProgress() {
        if (!isTaskPrepared()) return 0;
        return toZipTask.getTaskMaxProgress() * backup.getStorage().getZipProgressMultiplier() +
                deleteFolderTask.getTaskMaxProgress() * backup.getStorage().getDeleteProgressMultiplier();
    }

    @Override
    public long getTaskCurrentProgress() {
        if (!isTaskPrepared()) return 0;
        return toZipTask.getTaskCurrentProgress() * backup.getStorage().getZipProgressMultiplier() +
                deleteFolderTask.getTaskCurrentProgress() * backup.getStorage().getDeleteProgressMultiplier();
    }
}
