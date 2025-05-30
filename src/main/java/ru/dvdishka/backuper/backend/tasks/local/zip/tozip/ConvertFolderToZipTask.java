package ru.dvdishka.backuper.backend.tasks.local.zip.tozip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.util.List;

public class ConvertFolderToZipTask extends Task {

    private static final String taskName = "ToZip";

    private final File sourceFolderDir;

    private AddDirToZipTask addDirToZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long zipProgressMultiplier = 10;

    public ConvertFolderToZipTask(File sourceFolderDir, boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
        this.sourceFolderDir = sourceFolderDir;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {

            Logger.getLogger().devLog(taskName + " task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (!cancelled) {
                addDirToZipTask.run();
            }

            if (!cancelled) {
                deleteDirTask.run();
            }

            if (!cancelled) {
                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
                if (!new File(sourceFolderDir.getPath() + " in progress.zip").renameTo(new File(sourceFolderDir.getPath() + ".zip"))) {
                    Logger.getLogger().warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
                }
                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
            }

            Logger.getLogger().devLog("FolderToZip task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running FolderToZip task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    @Override
    public void prepareTask() {

        this.isTaskPrepared = true;

        addDirToZipTask = new AddDirToZipTask(List.of(sourceFolderDir), new File(sourceFolderDir.getPath() + " in progress.zip"), false, true, false, permissions, sender);
        addDirToZipTask.prepareTask();

        deleteDirTask = new DeleteDirTask(sourceFolderDir, false, permissions, sender);
        deleteDirTask.prepareTask();
    }

    @Override
    public long getTaskCurrentProgress() {

        if (addDirToZipTask == null || deleteDirTask == null) {
            return 0;
        }

        return addDirToZipTask.getTaskCurrentProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskCurrentProgress() * deleteProgressMultiplier;
    }

    @Override
    public long getTaskMaxProgress() {

        if (addDirToZipTask == null || deleteDirTask == null) {
            return 100;
        }

        return addDirToZipTask.getTaskMaxProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskMaxProgress() * deleteProgressMultiplier;
    }

    @Override
    public void cancel() {
        cancelled = true;

        deleteDirTask.cancel();
        addDirToZipTask.cancel();
    }
}
