package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;

import static java.lang.Long.max;

public class CopyToTask extends BaseTask {

    private final Backup sourceBackup;
    private final Storage targetStorage;

    private TransferDirTask copyToTask;

    public CopyToTask(Backup sourceBackup, Storage targetStorage) {
        super();
        this.sourceBackup = sourceBackup;
        this.targetStorage = targetStorage;
    }

    @Override
    public void run() {
        try {
            if (!cancelled) {
                Backuper.getInstance().getTaskManager().startTaskRaw(copyToTask, sender);
                targetStorage.renameFile(targetStorage.resolve(targetStorage.getConfig().getBackupsFolder(), sourceBackup.getInProgressFileName()), sourceBackup.getFileName());
            }
            if (!cancelled && Backup.BackupFileType.DIR.equals(sourceBackup.getFileType()))
                targetStorage.getBackupManager().saveBackupSizeToCache(sourceBackup.getName(), sourceBackup.getByteSize());
        } catch (Exception e) {
            warn(new TaskException(copyToTask, e));
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws Throwable {
        if (cancelled) return;
        copyToTask = new TransferDirTask(
                sourceBackup.getStorage(),
                sourceBackup.getPath(),
                targetStorage,
                targetStorage.resolve(targetStorage.getConfig().getBackupsFolder(), sourceBackup.getInProgressFileName()),
                false);
        Backuper.getInstance().getTaskManager().prepareTask(copyToTask, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
        if (copyToTask != null) Backuper.getInstance().getTaskManager().cancelTaskRaw(copyToTask);
    }

    @Override
    public long getTaskMaxProgress() {
        if (!isTaskPrepared()) return 0;
        return copyToTask.getTaskMaxProgress() *
                max(sourceBackup.getStorage().getTransferProgressMultiplier(), targetStorage.getTransferProgressMultiplier());
    }

    @Override
    public long getTaskCurrentProgress() {
        if (!isTaskPrepared()) return 0;
        return copyToTask.getTaskCurrentProgress() *
                max(sourceBackup.getStorage().getTransferProgressMultiplier(), targetStorage.getTransferProgressMultiplier());
    }
}
