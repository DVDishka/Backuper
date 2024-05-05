package ru.dvdishka.backuper.backend.tasks.zip.unzip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;

import java.io.File;

public class ConvertZipToFolderTask extends Task {

    private static final String taskName = "UnZip";

    private final File sourceZipFileDir;

    private UnpackZipTask unpackZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long unZipProgressMultiplier = 50;

    public ConvertZipToFolderTask(File sourceZipFileDir, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.sourceZipFileDir = sourceZipFileDir;
    }


    @Override
    public void run() {

        if (setLocked) {
            Backup.lock(this);
        }

        try {

            Logger.getLogger().devLog("ZipToFolder task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            Logger.getLogger().devLog("UnpackZip task has been started");
            unpackZipTask.run();
            Logger.getLogger().devLog("UnpackZip task has been finished");

            Logger.getLogger().devLog("DeleteDir task has been started");
            deleteDirTask.run();
            Logger.getLogger().devLog("DeleteDir task has been started");

            Logger.getLogger().devLog("ZipToFolder task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backup.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backup.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running ZipToFolder task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public void prepareTask() {

        this.isTaskPrepared = true;

        unpackZipTask = new UnpackZipTask(sourceZipFileDir, new File(sourceZipFileDir.getPath().replace(".zip", "")), false, sender);
        deleteDirTask = new DeleteDirTask(sourceZipFileDir, false, sender);
    }

    @Override
    public long getTaskCurrentProgress() {
        return unpackZipTask.getTaskCurrentProgress() * unZipProgressMultiplier + deleteDirTask.getTaskCurrentProgress() * deleteProgressMultiplier;
    }

    @Override
    public long getTaskMaxProgress() {
        return unpackZipTask.getTaskMaxProgress() * unZipProgressMultiplier + deleteDirTask.getTaskMaxProgress() * deleteProgressMultiplier;
    }
}
