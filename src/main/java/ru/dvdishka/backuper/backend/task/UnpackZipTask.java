package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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

        try (ZipInputStream zipInput = new ZipInputStream(storage.downloadFile(sourceZipDir, new BasicStorageProgressListener()))) {

            ZipEntry zipEntry;
            if (!storage.exists(targetFolderDir)) {
                storage.createDir(storage.getFileNameFromPath(sourceZipDir), storage.getParentPath(targetFolderDir));
            }

            while (!cancelled && (zipEntry = zipInput.getNextEntry()) != null) {
                final String name = zipEntry.getName();
                try {

                    if (zipEntry.isDirectory()) {
                        storage.createDir(storage.getFileNameFromPath(name), storage.resolve(targetFolderDir, storage.getParentPath(name)));
                    } else {
                        StorageProgressListener progressListener = new BasicStorageProgressListener();
                        storage.uploadFile(zipInput, storage.getFileNameFromPath(name), storage.resolve(targetFolderDir, name), progressListener);
                        progressListeners.add(progressListener);
                    }

                } catch (Exception e) {
                    warn("Something went wrong while trying to unpack file", sender);
                    warn(e);
                }

                zipInput.closeEntry();
            }

            storage.renameFile(targetFolderDir, storage.getFileNameFromPath(sourceZipDir).replace(" in progress", ""));
        }
    }

    @Override
    public void prepareTask(CommandSender sender) {

        maxProgress = 0;

        try (ZipFile zipFile = new ZipFile(sourceZipDir)) {

            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

            while (zipEntries.hasMoreElements()) {

                if (cancelled) {
                    break;
                }

                ZipEntry zipEntry = zipEntries.nextElement();
                maxProgress += zipEntry.getSize();
            }

        } catch (Exception e) {
            warn("Something went wrong while calculating UnpackZip task maxProgress", this.sender);
            warn(e);
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
}
