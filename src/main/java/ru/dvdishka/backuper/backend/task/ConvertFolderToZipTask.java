package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ConvertFolderToZipTask extends BaseTask {

    private final Storage storage;
    private final File sourceFolderDir;

    private UploadDirsAsZip uploadDirsAsZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long zipProgressMultiplier = 10;

    public ConvertFolderToZipTask(Storage storage, File sourceFolderDir) {
        this.storage = storage;
        this.sourceFolderDir = sourceFolderDir;
    }

    @Override
    public void run() throws IOException {

        if (!cancelled) {
            try {
                Backuper.getInstance().getTaskManager().startTaskRaw(uploadDirsAsZipTask, sender);
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
            if (!new File("%s in progress.zip".formatted(sourceFolderDir.getPath())).renameTo(new File("%s.zip".formatted(sourceFolderDir.getPath())))) {
                warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
            }
            devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException {

                        uploadDirsAsZipTask = new UploadDirsAsZip(storage, List.of(sourceFolderDir), sourceFolderDir.toPath().toAbsolutePath().normalize().getParent().toString(), "%s in progress.zip".formatted(sourceFolderDir.getName()), false, true);
        Backuper.getInstance().getTaskManager().prepareTask(uploadDirsAsZipTask, sender);

        deleteDirTask = new DeleteDirTask(storage, sourceFolderDir.toPath().toAbsolutePath().normalize().toString());
        Backuper.getInstance().getTaskManager().prepareTask(deleteDirTask, sender);
    }

    @Override
    public long getTaskCurrentProgress() {

        if (uploadDirsAsZipTask == null || deleteDirTask == null) {
            return 0;
        }

        return uploadDirsAsZipTask.getTaskCurrentProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskCurrentProgress() * deleteProgressMultiplier;
    }

    @Override
    public long getTaskMaxProgress() {

        if (uploadDirsAsZipTask == null || deleteDirTask == null) {
            return 100;
        }

        return uploadDirsAsZipTask.getTaskMaxProgress() * zipProgressMultiplier +
                deleteDirTask.getTaskMaxProgress() * deleteProgressMultiplier;
    }

    @Override
    public void cancel() {
        cancelled = true;
        Backuper.getInstance().getTaskManager().cancelTaskRaw(uploadDirsAsZipTask);
        Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteDirTask);
    }
}
