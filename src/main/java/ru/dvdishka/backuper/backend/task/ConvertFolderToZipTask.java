package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ConvertFolderToZipTask extends BaseAsyncTask {

    private final File sourceFolderDir;

    private AddLocalDirToZipTask addLocalDirToZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long zipProgressMultiplier = 10;

    public ConvertFolderToZipTask(File sourceFolderDir) {

        super();
        this.sourceFolderDir = sourceFolderDir;
    }

    @Override
    protected void run() throws IOException {

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(addLocalDirToZipTask, sender);
            } catch (Exception e) {
                warn(e);
            }
        }

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(deleteDirTask, sender);
            } catch (Exception e) {
                warn(e);
            }
        }

        if (!cancelled) {
            devLog("The Rename \"in progress\" Folder/ZIP local task has been started");
            if (!new File(sourceFolderDir.getPath() + " in progress.zip").renameTo(new File(sourceFolderDir.getPath() + ".zip"))) {
                warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
            }
            devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

        addLocalDirToZipTask = new AddLocalDirToZipTask(List.of(sourceFolderDir), new File(sourceFolderDir.getPath() + " in progress.zip"), false, true);
        Backuper.getInstance().getTaskManager().prepareTask(addLocalDirToZipTask, sender);

        deleteDirTask = new DeleteDirTask(sourceFolderDir);
        Backuper.getInstance().getTaskManager().prepareTask(deleteDirTask, sender);
    }

    @Override
    public long getTaskCurrentProgress() {

        if (addLocalDirToZipTask == null || deleteDirTask == null) {
            return 0;
        }

        return addLocalDirToZipTask.getTaskCurrentProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskCurrentProgress() * deleteProgressMultiplier;
    }

    @Override
    public long getTaskMaxProgress() {

        if (addLocalDirToZipTask == null || deleteDirTask == null) {
            return 100;
        }

        return addLocalDirToZipTask.getTaskMaxProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskMaxProgress() * deleteProgressMultiplier;
    }

    @Override
    protected void cancel() {
        cancelled = true;
        Backuper.getInstance().getTaskManager().cancelTaskRaw(addLocalDirToZipTask);
        Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteDirTask);
    }
}
