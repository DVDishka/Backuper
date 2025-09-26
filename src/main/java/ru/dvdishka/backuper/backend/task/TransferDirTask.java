package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.LocalStorage;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.util.Utils;

import javax.naming.AuthenticationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class TransferDirTask extends BaseTask implements DoubleStorageTask {

    private final Storage sourceStorage;
    private final String sourceDir;
    private final Storage targetStorage;
    private String targetDir;
    private final boolean forceExcludedDirs;

    private ArrayList<StorageProgressListener> progressListeners;
    private long dirSize = 0;

    private static final int FILE_BUFFER_SIZE = 65536;

    /***
     * @param targetDir Not parent
     */
    public TransferDirTask(Storage sourceStorage, String sourceDir, Storage targetStorage, String targetDir, boolean forceExcludedDirs) {
        super();
        this.sourceStorage = sourceStorage;
        this.targetStorage = targetStorage;
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() {

        if (!targetStorage.exists(targetDir) && sourceStorage.isDir(sourceDir)) {
            targetStorage.createDir(targetStorage.getFileNameFromPath(targetDir), targetStorage.getParentPath(targetDir));
        }

        progressListeners = new ArrayList<>();
        if (!cancelled) {
            sendFolder(sourceDir, targetDir);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException, AuthenticationException, IOException, Storage.StorageLimitException, Storage.StorageQuotaExceededException, SftpException {
        if (sourceStorage instanceof LocalStorage && !forceExcludedDirs) {
            dirSize = Utils.getFileFolderByteSizeExceptExcluded(new File(sourceDir));
        } else {
            dirSize = sourceStorage.getDirByteSize(sourceDir);
        }
    }

    private void sendFolder(final String sourceDir, final String targetDir) {
        if (cancelled) return;

        if (!sourceStorage.exists(sourceDir)) {
            warn("Something went wrong while trying to send files from %s".formatted(sourceDir));
            warn("Directory %s doesn't exist".formatted(sourceDir), sender);
            return;
        }

        if (sourceStorage instanceof LocalStorage && !forceExcludedDirs) {
            if (Utils.isExcludedDirectory(new File(sourceDir), sender)) return;
        }

        if (sourceStorage.isFile(sourceDir) && !sourceStorage.getFileNameFromPath(sourceDir).equals("session.lock")) {
            try {
                final StorageProgressListener progressListener = new BasicStorageProgressListener();
                progressListeners.add(progressListener);
                try (BufferedInputStream inputStream = new BufferedInputStream(sourceStorage.downloadFile(sourceDir), FILE_BUFFER_SIZE)) {
                    targetStorage.uploadFile(inputStream, sourceStorage.getFileNameFromPath(sourceDir), targetStorage.getParentPath(targetDir), progressListener);
                } catch (Exception e) {
                    warn("Failed to send file \"%s\" to %s storage".formatted(sourceDir, targetStorage.getId()));
                    warn(e);
                } finally {
                    sourceStorage.downloadCompleted();
                }

            } catch (Exception e) {
                warn("Failed to send file \"%s\" to %s storage".formatted(sourceDir, targetStorage.getId()), sender);
                warn(e);
            }
        }
        if (sourceStorage.isDir(sourceDir)) {
            for (String file : sourceStorage.ls(sourceDir)) {
                if (sourceStorage.isDir(sourceStorage.resolve(sourceDir, file))) {
                    targetStorage.createDir(file, targetDir);
                }
                sendFolder(sourceStorage.resolve(sourceDir, file), targetStorage.resolve(targetDir, file));
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        if (progressListeners == null) return 0;
        return progressListeners.stream().mapToLong(StorageProgressListener::getCurrentProgress).sum();
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public Storage getSourceStorage() {
        return sourceStorage;
    }

    @Override
    public Storage getTargetStorage() {
        return targetStorage;
    }
}
