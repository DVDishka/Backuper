package ru.dvdishka.backuper.backend.tasks.googleDrive;

import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GoogleDriveGetFileFolderTask extends Task {

    private static final String taskName = "GoogleDriveGetFileFolder";

    private final String sourceDirId;
    private File targetDir;
    private final boolean createRootDirInTargetDir;
    private final boolean setLocked;
    private final List<Permissions> permissions;
    private final CommandSender sender;

    private long dirSize = 0;
    private ArrayList<CompletableFuture<Void>> tasks = new ArrayList<>();
    private ArrayList<GoogleDriveDownloadProgressListener> progressListeners = new ArrayList<>();

    public GoogleDriveGetFileFolderTask(String sourceDirId, File targetDir, boolean createRootDirInTargetDir,
                                        boolean setLocked, List<Permissions> permissions, CommandSender sender) {
        super(taskName, setLocked, permissions, sender);

        this.sourceDirId = sourceDirId;
        this.targetDir = targetDir;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.setLocked = setLocked;
        this.permissions = permissions;
        this.sender = sender;
    }

    @Override
    public void run() {

        try {

            if (setLocked) {
                Backuper.lock(this);
            }

            if (!isTaskPrepared) {
                prepareTask();
            }

            Logger.getLogger().devLog(taskName + " task has been started");

            if (!cancelled) {
                getRemoteDir(sourceDirId, targetDir, true);
            }

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong when trying to download file \"" + sourceDirId + "\" from Google Drive", sender);
            Logger.getLogger().warn(this.getClass(), e);
        } finally {
            Logger.getLogger().devLog(taskName + " task has been finished");
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        try {
            dirSize = GoogleDriveUtils.getFileByteSize(sourceDirId, sender);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get directory size from Google Drive", e);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    private void getRemoteDir(String driveFileId, File localPath, boolean firstDir) {

        if (cancelled) {
            return;
        }

        try {

            if (!GoogleDriveUtils.isFolder(driveFileId, sender)) {

                localPath.createNewFile();

                GoogleDriveDownloadProgressListener progressListener = new GoogleDriveDownloadProgressListener();
                progressListeners.add(progressListener);

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    try {
                        GoogleDriveUtils.downloadFile(driveFileId, localPath, progressListener, sender);
                    } catch (Exception e) {
                        Logger.getLogger().devWarn(this.getClass(), "Something went wrong when trying to download file \"" + driveFileId + "\" from Google Drive");
                        Logger.getLogger().devWarn(this.getClass(), e);
                    }
                });

                tasks.add(task);
                try {
                    task.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        Logger.getLogger().warn("Something went wrong when trying to download file \"" + driveFileId + "\" from Google Drive", sender);
                        Logger.getLogger().warn(this.getClass(), e);
                    }
                }

            } else {

                Path newPath = localPath.toPath();
                localPath.mkdirs();

                if (firstDir && createRootDirInTargetDir) {
                    newPath = newPath.resolve(GoogleDriveUtils.getFileName(driveFileId, sender));
                }

                for (com.google.api.services.drive.model.File driveEntry : GoogleDriveUtils.ls(driveFileId, sender)) {
                    getRemoteDir(driveEntry.getId(), newPath.resolve(driveEntry.getName()).toFile(), false);
                }
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to get file/folder from Google Drive", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    @Override
    public void cancel() {
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
