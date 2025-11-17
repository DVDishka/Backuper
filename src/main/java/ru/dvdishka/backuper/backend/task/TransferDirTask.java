package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.storage.LocalStorage;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageQuotaExceededException;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.Utils;

import javax.naming.AuthenticationException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class TransferDirTask extends BaseTask implements DoubleStorageTask {

    private final Storage sourceStorage;
    private final String sourceDir;
    private final Storage targetStorage;
    private final String targetParentDir; // We have to provide parent path, not target because if it's a file, not a dir it has no id in id-based storages
    private final String targetFileName;
    private final boolean forceExcludedDirs;

    private ArrayList<StorageProgressListener> downloadProgressListeners;

    private static final int STREAM_BUFFER_SIZE = 1048576;

    public TransferDirTask(Storage sourceStorage, String sourceDir, Storage targetStorage, String targetParentDir, String targetFileName, boolean forceExcludedDirs) {
        super();
        this.sourceStorage = sourceStorage;
        this.targetStorage = targetStorage;
        this.sourceDir = sourceDir;
        this.targetParentDir = targetParentDir;
        this.targetFileName = targetFileName;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() {
        downloadProgressListeners = new ArrayList<>();
        if (!cancelled) {
            sendFolder(sourceDir, targetParentDir, targetFileName);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException, AuthenticationException, IOException, StorageLimitException, StorageQuotaExceededException, SftpException {
        if (maxProgress != 0) return;
        if (sourceStorage instanceof LocalStorage && !forceExcludedDirs) {
            maxProgress = Utils.getFileFolderByteSizeExceptExcluded(new File(sourceDir));
        } else {
            maxProgress = sourceStorage.getDirByteSize(sourceDir);
        }
    }

    private void sendFolder(final String sourceDir, final String targetParentDir, String targetFileName) { // We have to provide parent path, not target because if it's a file, not a dir it has no id in id-based storages
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
                downloadProgressListeners.add(progressListener);
                try (InputStream directInputStream = sourceStorage.downloadFile(sourceDir, progressListener);
                     BufferedInputStream inputStream = new BufferedInputStream(directInputStream, STREAM_BUFFER_SIZE)) {
                    targetStorage.uploadFile(inputStream, targetFileName, targetParentDir);
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
            targetStorage.createDir(targetFileName, targetParentDir);
            for (String file : sourceStorage.ls(sourceDir)) {
                sendFolder(sourceStorage.resolve(sourceDir, file), targetStorage.resolve(targetParentDir, targetFileName), file);
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        if (downloadProgressListeners == null) return 0;
        return downloadProgressListeners.stream().mapToLong(StorageProgressListener::getCurrentProgress).sum();
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
