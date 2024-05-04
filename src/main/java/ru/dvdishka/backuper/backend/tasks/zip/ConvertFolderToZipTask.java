package ru.dvdishka.backuper.backend.tasks.zip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;

import java.io.File;

public class ConvertFolderToZipTask extends Task {

    private static final String taskName = "ToZip";

    private final File sourceFolderDir;

    private AddDirToZipTask addDirToZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long zipProgressMultiplier = 10;

    public ConvertFolderToZipTask(File sourceFolderDir, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.sourceFolderDir = sourceFolderDir;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backup.lock(this);
        }

        try {

            Logger.getLogger().devLog("FolderToZip task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            Logger.getLogger().devLog("Pack To Zip task has been started");
            addDirToZipTask.run();
            Logger.getLogger().devLog("Pack To Zip task has been finished");

            Logger.getLogger().devLog("Delete Folder task has been started");
            deleteDirTask.run();
            Logger.getLogger().devLog("Delete Folder task has been finished");

            Logger.getLogger().devLog("FolderToZip task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backup.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backup.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running FolderToZip task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public void prepareTask() {

        this.isTaskPrepared = true;

        addDirToZipTask = new AddDirToZipTask(sourceFolderDir, new File(sourceFolderDir.getPath() + ".zip"), false, true, false, sender);
        addDirToZipTask.prepareTask();

        deleteDirTask = new DeleteDirTask(sourceFolderDir, false, sender);
        deleteDirTask.prepareTask();
    }

    @Override
    public long getTaskCurrentProgress() {

        return addDirToZipTask.getTaskCurrentProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskCurrentProgress() * deleteProgressMultiplier;
    }

    @Override
    public long getTaskMaxProgress() {

        return addDirToZipTask.getTaskMaxProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskMaxProgress() * deleteProgressMultiplier;
    }
}
