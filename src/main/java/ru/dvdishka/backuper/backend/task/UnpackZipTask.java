package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnpackZipTask extends BaseTask implements SingleStorageTask {

    private final Storage storage;
    private final String sourceZipDir;
    private final String targetFolderDir;

    private final List<StorageProgressListener> progressListeners = new ArrayList<>();

    /***
     * @param targetFolderDir Not parent
     */
    public UnpackZipTask(Storage storage, String sourceZipDir, String targetFolderDir) {
        super();
        this.storage = storage;
        this.sourceZipDir = sourceZipDir;
        this.targetFolderDir = "%s in progress".formatted(targetFolderDir);
    }

    @Override
    public void run() throws IOException {

        try (InputStream inputStream = storage.downloadFile(sourceZipDir);
             ZipInputStream zipInput = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            if (!storage.exists(targetFolderDir)) {
                storage.createDir(storage.getFileNameFromPath(targetFolderDir), storage.getParentPath(targetFolderDir));
            }

            while (!cancelled && (zipEntry = zipInput.getNextEntry()) != null) {
                final String name = zipEntry.getName();
                try {
                    if (zipEntry.isDirectory()) {
                        storage.createDir(getFileNameFromZipPath(name), storage.resolve(targetFolderDir, getParentFromZipPath(name)));
                    } else {
                        StorageProgressListener progressListener = new BasicStorageProgressListener();
                        progressListeners.add(progressListener);
                        storage.uploadFile(zipInput, getFileNameFromZipPath(name), storage.resolve(targetFolderDir, getParentFromZipPath(name)), progressListener);
                    }
                } catch (Exception e) {
                    warn("Something went wrong while trying to unpack file", sender);
                    warn(e);
                }
                zipInput.closeEntry();
            }

            storage.renameFile(targetFolderDir, storage.getFileNameFromPath(targetFolderDir).replace(" in progress", ""));
        } finally {
            storage.downloadCompleted();
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws IOException {
        try (InputStream inputStream = storage.downloadFile(sourceZipDir);
             ZipInputStream zipInput = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while (!cancelled && (zipEntry = zipInput.getNextEntry()) != null) {
                maxProgress += zipEntry.getSize();
                zipInput.closeEntry();
            }
        } finally {
            storage.downloadCompleted();
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        return progressListeners.stream().mapToLong(StorageProgressListener::getCurrentProgress).sum();
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
