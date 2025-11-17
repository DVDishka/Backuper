package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.Storage;

public class DeleteDirTask extends BaseTask implements SingleStorageTask {

    private final Storage storage;
    private final String path;

    public DeleteDirTask(Storage storage, String path) {
        this.storage = storage;
        this.path = path;
    }


    @Override
    public void run() {
        if (!cancelled) {
            deleteDir(path);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws SftpException {
        if (maxProgress != 0) return;
        maxProgress = storage.getDirByteSize(path);
    }

    private void deleteDir(String currentPath) {

        if (cancelled) return;
        try {
            if (storage.isDir(currentPath)) {
                for (String file : storage.ls(currentPath)) {
                    deleteDir(storage.resolve(currentPath, file));
                }
                storage.delete(currentPath);
            } else {
                long fileSize = storage.getDirByteSize(currentPath);
                storage.delete(currentPath);
                incrementCurrentProgress(fileSize);
            }
        } catch (Exception e) {
            warn("Something went while trying to delete %s directory from %s storage".formatted(currentPath, storage.getId()), sender);
            warn(e);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public Storage getStorage() {
        return storage;
    }
}
