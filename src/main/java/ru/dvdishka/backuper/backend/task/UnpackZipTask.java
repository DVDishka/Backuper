package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;

import java.io.BufferedInputStream;
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

    private StorageProgressListener downloadProgressListener;
    private final List<StorageProgressListener> uploadProgressListeners = new ArrayList<>();

    private static final int STREAM_BUFFER_SIZE = 1048576;

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
        downloadProgressListener = new BasicStorageProgressListener();
        try (InputStream directInputStream = storage.downloadFile(sourceZipDir, downloadProgressListener);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(directInputStream, STREAM_BUFFER_SIZE);
             ZipInputStream zipInput = new ZipInputStream(bufferedInputStream)) {
            ZipEntry zipEntry;
            if (!storage.exists(targetFolderDir)) {
                storage.createDir(storage.getFileNameFromPath(targetFolderDir), storage.getParentPath(targetFolderDir));
            }

            while (!cancelled && (zipEntry = zipInput.getNextEntry()) != null) {
                String name = zipEntry.getName();
                try {
                    List<String> entryRelativeListedPath = new ArrayList<>();
                    if (zipEntry.isDirectory()) name = name.substring(0, name.length() - 1);
                    String currentRelativePath = name;
                    while (!(currentRelativePath = getParentFromZipPath(currentRelativePath)).isEmpty()) entryRelativeListedPath.add(getFileNameFromZipPath(currentRelativePath));
                    entryRelativeListedPath = entryRelativeListedPath.reversed();
                    String entryParentRelativePath = targetFolderDir;
                    for (String dir : entryRelativeListedPath) {
                        entryParentRelativePath = storage.resolve(entryParentRelativePath, dir);
                    }

                    if (zipEntry.isDirectory()) {
                        storage.createDir(getFileNameFromZipPath(name), entryParentRelativePath);
                    } else {
                        StorageProgressListener uploadProgressListener = new BasicStorageProgressListener();
                        uploadProgressListeners.add(uploadProgressListener);
                        storage.uploadFile(zipInput, getFileNameFromZipPath(name), entryParentRelativePath, uploadProgressListener);
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
        if (maxProgress != 0) return;
        maxProgress = storage.getDirByteSize(sourceZipDir);
    }

    @Override
    public long getTaskCurrentProgress() {
        return downloadProgressListener.getCurrentProgress();
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

    public long getBytesUploaded() {
        return uploadProgressListeners.stream().mapToLong(StorageProgressListener::getCurrentProgress).sum();
    }
}
