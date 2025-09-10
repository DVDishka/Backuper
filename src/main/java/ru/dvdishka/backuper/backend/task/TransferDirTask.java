package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.LocalStorage;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.Utils;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TransferDirTask extends BaseTask implements DoubleStorageTask {

    private final Storage sourceStorage;
    private final String sourceDir;
    private final Storage targetStorage;
    private String targetDir;
    private final boolean createRootDirInTargetDir;
    private final boolean forceExcludedDirs;

    private final ArrayList<CompletableFuture<Void>> jobs = new ArrayList<>();
    private ArrayList<StorageProgressListener> progressListeners;
    private long dirSize = 0;

    /***
     * @param targetDir Not parent
     */
    public TransferDirTask(Storage sourceStorage, String sourceDir, Storage targetStorage, String targetDir, boolean createRootDirInTargetDir, boolean forceExcludedDirs) {
        super();
        this.sourceStorage = sourceStorage;
        this.targetStorage = targetStorage;
        this.sourceDir = sourceDir;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.targetDir = targetDir;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() throws IOException, JSchException, SftpException {

        if (createRootDirInTargetDir) {
            if (sourceStorage.isDir(sourceDir)) {
                targetStorage.createDir(sourceStorage.getFileNameFromPath(sourceDir), targetDir);
            }
            targetDir = targetStorage.resolve(targetDir, sourceStorage.getFileNameFromPath(sourceDir));
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
                CompletableFuture<Void> job = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try (InputStream inputStream = sourceStorage.downloadFile(sourceDir, new BasicStorageProgressListener())) {
                        targetStorage.uploadFile(inputStream, sourceStorage.getFileNameFromPath(sourceDir), targetStorage.getParentPath(targetDir), progressListener);
                    } catch (Exception e) {
                        warn("Failed to send file \"%s\" to %s storage".formatted(sourceDir, targetStorage.getId()));
                        warn(e);
                    }
                });

                jobs.add(job);
                try {
                    job.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Failed to send file \"%s\" to %s storage".formatted(sourceDir, targetStorage.getId()), sender);
                        warn(e);
                    }
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
        if (progressListeners == null) {
            return 0;
        }
        long currentProgress = 0;
        for (StorageProgressListener progressListener : progressListeners) {
            currentProgress += progressListener.getCurrentProgress();
        }
        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    @Override
    public void cancel() {
        cancelled = true;
        for (CompletableFuture<Void> job : jobs) {
            job.cancel(true);
        }
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
