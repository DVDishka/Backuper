package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;

import java.io.File;

public class ConvertZipToFolderTask extends BaseAsyncTask {

    private final File sourceZipFileDir;

    private UnpackZipTask unpackZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long unZipProgressMultiplier = 50;

    public ConvertZipToFolderTask(File sourceZipFileDir) {

        super();
        this.sourceZipFileDir = sourceZipFileDir;
    }

    @Override
    protected void run() {

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(unpackZipTask, sender);
            } catch (TaskException e) {
                warn(e);
            }
        }

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(deleteDirTask, sender);
            } catch (TaskException e) {
                warn(e);
            }
        }

        if (!cancelled) {
            devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
            if (!new File(sourceZipFileDir.getPath().replace(".zip", "") + " in progress").renameTo(new File(sourceZipFileDir.getPath().replace(".zip", "")))) {
                warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
            }
            devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {

        unpackZipTask = new UnpackZipTask(sourceZipFileDir, new File(sourceZipFileDir.getPath().replace(".zip", "") + " in progress"));
        deleteDirTask = new DeleteDirTask(sourceZipFileDir);
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
    protected void cancel() {
        cancelled = true;
        Backuper.getInstance().getTaskManager().cancelTaskRaw(unpackZipTask);
        Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteDirTask);
    }
}
