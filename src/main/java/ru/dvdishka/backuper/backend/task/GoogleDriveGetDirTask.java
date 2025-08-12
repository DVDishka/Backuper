package ru.dvdishka.backuper.backend.task;

import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class GoogleDriveGetDirTask extends BaseAsyncTask {

    private final String sourceDirId;
    private final File targetDir;
    private final boolean createRootDirInTargetDir;

    private long dirSize = 0;
    private final ArrayList<CompletableFuture<Void>> tasks = new ArrayList<>();
    private final ArrayList<GoogleDriveDownloadProgressListener> progressListeners = new ArrayList<>();

    public GoogleDriveGetDirTask(String sourceDirId, File targetDir, boolean createRootDirInTargetDir) {
        super();

        this.sourceDirId = sourceDirId;
        this.targetDir = targetDir;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    protected void run() {
        if (!cancelled) {
            getRemoteDir(sourceDirId, targetDir, true);
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        try {
            dirSize = GoogleDriveUtils.getFileByteSize(sourceDirId);
        } catch (Exception e) {
            warn("Failed to get directory size from Google Drive", this.sender);
            warn(e);
        }
    }

    private void getRemoteDir(String driveFileId, File localPath, boolean firstDir) {

        if (cancelled) {
            return;
        }

        try {

            if (!GoogleDriveUtils.isFolder(driveFileId)) {

                localPath.createNewFile();

                GoogleDriveDownloadProgressListener progressListener = new GoogleDriveDownloadProgressListener();
                progressListeners.add(progressListener);

                CompletableFuture<Void> task = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try {
                        GoogleDriveUtils.downloadFile(driveFileId, localPath, progressListener);
                    } catch (Exception e) {
                        devWarn("Something went wrong when trying to download file \"" + driveFileId + "\" from Google Drive");
                        devWarn(e);
                    }
                });

                tasks.add(task);
                try {
                    task.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Something went wrong when trying to download file \"" + driveFileId + "\" from Google Drive", sender);
                        warn(e);
                    }
                }

            } else {

                Path newPath = localPath.toPath();
                localPath.mkdirs();

                if (firstDir && createRootDirInTargetDir) {
                    newPath = newPath.resolve(GoogleDriveUtils.getFileName(driveFileId));
                }

                for (com.google.api.services.drive.model.File driveEntry : GoogleDriveUtils.ls(driveFileId)) {
                    getRemoteDir(driveEntry.getId(), newPath.resolve(driveEntry.getName()).toFile(), false);
                }
            }
        } catch (Exception e) {
            warn("Failed to get file %s from Google Drive".formatted(driveFileId), sender);
            warn(e);
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : tasks) {
            task.cancel(true);
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        long currentProgress = 0;

        for (GoogleDriveDownloadProgressListener progressListener : progressListeners) {
            currentProgress += progressListener.getBytesDownloaded();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    private static class GoogleDriveDownloadProgressListener implements MediaHttpDownloaderProgressListener {

        long progress = 0;

        @Override
        public void progressChanged(MediaHttpDownloader mediaHttpDownloader) {
            progress = mediaHttpDownloader.getNumBytesDownloaded();
        }

        public long getBytesDownloaded() {
            return progress;
        }
    }
}
