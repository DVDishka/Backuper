package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.Storage;

import java.io.File;

public class ConvertZipToFolderTask extends BaseTask {

    private final Storage storage;
    private final File sourceZipFileDir;

    private UnpackZipTask unpackZipTask;
    private DeleteDirTask deleteDirTask;

    private final long deleteProgressMultiplier = 1;
    private final long unZipProgressMultiplier = 50;

    public ConvertZipToFolderTask(Storage storage, File sourceZipFileDir) {
        this.storage = storage;
        this.sourceZipFileDir = sourceZipFileDir;
    }

    @Override
    public void run() {

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
            if (!new File("%s in progress".formatted(sourceZipFileDir.getPath().replace(".zip", ""))).renameTo(new File(sourceZipFileDir.getPath().replace(".zip", "")))) {
                warn("The Rename \"in progress\" ZIP local task has been finished with an exception!", sender);
            }
            devLog("The Rename \"in progress\" Folder/ZIP local task has been finished");
        }
    }

    @Override
    public void prepareTask(CommandSender sender) {
        unpackZipTask = new UnpackZipTask(sourceZipFileDir, new File("%s in progress".formatted(sourceZipFileDir.getPath().replace(".zip", ""))));
        deleteDirTask = new DeleteDirTask(storage, sourceZipFileDir.toPath().toAbsolutePath().normalize().toString());
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
        Backuper.getInstance().getTaskManager().cancelTaskRaw(unpackZipTask);
        Backuper.getInstance().getTaskManager().cancelTaskRaw(deleteDirTask);
    }
}
