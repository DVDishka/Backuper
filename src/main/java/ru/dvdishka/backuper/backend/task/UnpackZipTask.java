package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnpackZipTask extends BaseTask implements SingleStorageTask {

    private final Storage storage;
    private final String sourceZipDir;
    private final String targetFolderDir;

    private StorageProgressListener progressListener;

    /***
     * @param targetFolderDir Not parent
     */
    public UnpackZipTask(Storage storage, String sourceZipDir, String targetFolderDir) {
        super();
        this.storage = storage;
        this.sourceZipDir = sourceZipDir;
        this.targetFolderDir = targetFolderDir;
    }

    @Override
    public void run() {
        progressListener = new BasicStorageProgressListener();
        try (InputStream inputStream = storage.downloadFile(sourceZipDir, progressListener);
             ZipInputStream zipInput = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            if (!storage.exists(targetFolderDir)) {
                storage.createDir(storage.getFileNameFromPath(targetFolderDir), storage.getParentPath(targetFolderDir));
            }

            while (!cancelled && (zipEntry = zipInput.getNextEntry()) != null) {
                String name = zipEntry.getName();
                try {
                    if (zipEntry.isDirectory()) {
                        name = name.substring(0, name.length() - 1);
                        storage.createDir(getFileNameFromZipPath(name), storage.resolve(targetFolderDir, getParentFromZipPath(name)));
                    } else {
                        storage.uploadFile(zipInput, getFileNameFromZipPath(name), storage.resolve(targetFolderDir, getParentFromZipPath(name)));
                    }
                } catch (Exception e) {
                    warn("Something went wrong while trying to unpack file", sender);
                    warn(e);
                }
                zipInput.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            storage.downloadCompleted();
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws IOException {
        maxProgress = storage.getDirByteSize(sourceZipDir);
    }

    @Override
    public long getTaskCurrentProgress() {
        return progressListener.getCurrentProgress();
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public Storage getStorage() {
        return storage;
    }

    private String getFileNameFromZipPath(String path) {
        return path.substring(path.lastIndexOf("/") + 1);
    }

    private String getParentFromZipPath(String path) {
        return path.substring(0, path.lastIndexOf("/") == -1 ? 0 : path.lastIndexOf("/"));
    }
}
