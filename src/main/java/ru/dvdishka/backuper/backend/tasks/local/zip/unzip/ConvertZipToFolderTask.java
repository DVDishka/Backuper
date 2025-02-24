package ru.dvdishka.backuper.backend.tasks.local.zip.unzip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.tasks.local.folder.DeleteDirTask;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.util.List;

public class ConvertZipToFolderTask extends Task {

    private static final String taskName = "UnZip";

    private final File sourceZipFileDir;

    private UnpackZipTask unpackZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long unZipProgressMultiplier = 50;

    public ConvertZipToFolderTask(File sourceZipFileDir, boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
        this.sourceZipFileDir = sourceZipFileDir;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {

            Logger.getLogger().devLog("ZipToFolder task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            if (!cancelled) {
                unpackZipTask.run();
            }

            if (!cancelled) {
                deleteDirTask.run();
            }

            if (!cancelled) {
                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
                if (!new File(sourceZipFileDir.getPath().replace(".zip", "") + " in progress").renameTo(new File(sourceZipFileDir.getPath().replace(".zip", "")))) {
                    Logger.getLogger().warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
                }
                Logger.getLogger().devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
            }

            Logger.getLogger().devLog("ZipToFolder task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running ZipToFolder task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    @Override
    public void prepareTask() {

        this.isTaskPrepared = true;

        unpackZipTask = new UnpackZipTask(sourceZipFileDir, new File(sourceZipFileDir.getPath().replace(".zip", "") + " in progress"), false, permissions, sender);
        deleteDirTask = new DeleteDirTask(sourceZipFileDir, false, permissions, sender);
    }

    @Override
    public long getTaskCurrentProgress() {
        if (unpackZipTask == null || deleteDirTask == null) {
            return 0;
        }
        return unpackZipTask.getTaskCurrentProgress() * unZipProgressMultiplier + deleteDirTask.getTaskCurrentProgress() * deleteProgressMultiplier;
    }

    @Override
    public long getTaskMaxProgress() {
        if (unpackZipTask == null || deleteDirTask == null) {
            return 100;
        }
        return unpackZipTask.getTaskMaxProgress() * unZipProgressMultiplier + deleteDirTask.getTaskMaxProgress() * deleteProgressMultiplier;
    }

    @Override
    public void cancel() {
        cancelled = true;

        deleteDirTask.cancel();
        unpackZipTask.cancel();
    }
}
