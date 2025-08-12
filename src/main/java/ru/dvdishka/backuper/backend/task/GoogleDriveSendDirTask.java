package ru.dvdishka.backuper.backend.task;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.exception.StorageQuotaExceededException;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class GoogleDriveSendDirTask extends BaseAsyncTask {

    private final String targetFolderId;
    private final File sourceDir;
    private final boolean forceExcludedDirs;
    private final boolean createRootDirInTargetDir;
    private final String rootDirInTargetDirName;

    private long dirSize = 0;
    private final ArrayList<CompletableFuture<Void>> tasks = new ArrayList<>();
    private final ArrayList<GoogleDriveUploadProgressListener> progressListeners = new ArrayList<>();

    public GoogleDriveSendDirTask(File sourceDir, String targetDirId, String rootDirInTargetDirName,
                                  boolean createRootDirInTargetDir, boolean forceExcludedDirs) {
        super();

        this.sourceDir = sourceDir;
        this.targetFolderId = targetDirId;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.forceExcludedDirs = forceExcludedDirs;
        this.rootDirInTargetDirName = rootDirInTargetDirName;
    }

    @Override
    protected void run() {
        if (!cancelled) {
            try {
                sendFolder(sourceDir, targetFolderId, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        if (forceExcludedDirs) {
            dirSize = Utils.getFileFolderByteSize(sourceDir);
        } else {
            dirSize = Utils.getFileFolderByteSizeExceptExcluded(sourceDir);
        }
    }

    private void sendFolder(File localDirToSend, String remoteFolderId, boolean firstDir) throws StorageLimitException, StorageQuotaExceededException {

        if (remoteFolderId == null) {
            warn("remoteFolderId is null, let developer know!", sender);
            return;
        }

        if (cancelled) {
            return;
        }

        if (!localDirToSend.exists()) {
            warn("Something went wrong while trying to send files from %s".formatted(localDirToSend.getAbsolutePath()));
            warn("Directory %s does not exist".formatted(localDirToSend.getAbsolutePath()), sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(localDirToSend, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (localDirToSend.isFile() && !localDirToSend.getName().equals("session.lock")) {

            final String currentRemoteFolderId = remoteFolderId;
            final GoogleDriveUploadProgressListener progressListener = new GoogleDriveUploadProgressListener();
            progressListeners.add(progressListener);

            CompletableFuture<Void> task = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                try {
                    if (!firstDir) {
                        GoogleDriveUtils.uploadFile(localDirToSend, currentRemoteFolderId, progressListener);
                    } else {
                        GoogleDriveUtils.uploadFile(localDirToSend, rootDirInTargetDirName, currentRemoteFolderId, progressListener);
                    }
                } catch (StorageLimitException | StorageQuotaExceededException e) {
                    throw new RuntimeException(e);
                }
            });

            tasks.add(task);
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            if (createRootDirInTargetDir || !firstDir) {
                if (firstDir) {
                    remoteFolderId = GoogleDriveUtils.createFolder(rootDirInTargetDirName, remoteFolderId);
                } else {
                    remoteFolderId = GoogleDriveUtils.createFolder(localDirToSend.getName(), remoteFolderId);
                }
            }

            for (File file : localDirToSend.listFiles()) {
                sendFolder(file, remoteFolderId, false);
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        long currentProgress = 0;

        for (GoogleDriveUploadProgressListener progressListener : progressListeners) {
            currentProgress += progressListener.getBytesUploaded();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : tasks) {
            task.cancel(true);
        }
    }

    static class GoogleDriveUploadProgressListener implements MediaHttpUploaderProgressListener {

        long progress = 0;

        @Override
        public void progressChanged(MediaHttpUploader mediaHttpUploader) {
            progress = mediaHttpUploader.getNumBytesUploaded();
        }

        public long getBytesUploaded() {
            return progress;
        }
    }
}
