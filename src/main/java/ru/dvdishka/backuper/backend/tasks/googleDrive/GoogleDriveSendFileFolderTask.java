package ru.dvdishka.backuper.backend.tasks.googleDrive;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GoogleDriveSendFileFolderTask extends Task {

    private static final String taskName = "GoogleDriveSendFolder";

    private String targetFolderId;
    private final File sourceDir;
    private final boolean forceExcludedDirs;
    private final boolean createRootDirInTargetDir;

    private long dirSize = 0;
    private final ArrayList<CompletableFuture<Void>> tasks = new ArrayList<>();
    private final ArrayList<GoogleDriveUploadProgressListener> progressListeners = new ArrayList<>();

    public GoogleDriveSendFileFolderTask(File sourceDir, String targetDirId, boolean createRootDirInTargetDir,
                                         boolean forceExcludedDirs, boolean setLocked, List<Permissions> permissions,
                                         CommandSender sender) {
        super(taskName, setLocked, permissions, sender);

        this.sourceDir = sourceDir;
        this.targetFolderId = targetDirId;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.forceExcludedDirs = forceExcludedDirs;
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

            if (!GoogleDriveUtils.isAuthorized(sender)) {
                Logger.getLogger().warn(taskName + " task failed");
                Logger.getLogger().devLog("You need to login your Google account to upload files to Google Drive");
                return;
            }

            if (!cancelled && createRootDirInTargetDir) {
                targetFolderId = GoogleDriveUtils.createFolder(sourceDir.getName(), targetFolderId, sender);
            }

            if (!cancelled) {
                sendFolder(sourceDir, targetFolderId);
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

            Logger.getLogger().warn("Something went wrong when trying to upload file/folder to Google Drive", sender);
            Logger.getLogger().warn(this.getClass(), e);
        } finally {
            Logger.getLogger().devLog(taskName + " task has been finished");
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        if (forceExcludedDirs) {
            dirSize = Utils.getFileFolderByteSize(sourceDir);
        } else {
            dirSize = Utils.getFileFolderByteSizeExceptExcluded(sourceDir);
        }
    }

    private void sendFolder(File localDirToSend, String remoteFolderId) {

        if (cancelled) {
            return;
        }

        if (!localDirToSend.exists()) {
            Logger.getLogger().warn("Something went wrong while trying to send files from " + localDirToSend.getAbsolutePath());
            Logger.getLogger().warn("Directory " + localDirToSend.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(localDirToSend, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (localDirToSend.isFile() && !localDirToSend.getName().equals("session.lock")) {

            try {
                final String currentRemoteFolderId = remoteFolderId;
                final GoogleDriveUploadProgressListener progressListener = new GoogleDriveUploadProgressListener();
                progressListeners.add(progressListener);

                CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                    GoogleDriveUtils.uploadFile(localDirToSend, currentRemoteFolderId, progressListener, sender);
                });

                tasks.add(task);
                try {
                    task.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        Logger.getLogger().warn("Failed to upload file \"" + localDirToSend.getAbsolutePath() + "\" to Google Drive", sender);
                        Logger.getLogger().warn(this.getClass(), e);
                    }
                }

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while uploading file \"" + localDirToSend.getAbsolutePath() + "\" to the Google Drive", sender);
                Logger.getLogger().warn(this.getClass(), e);
            }
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            remoteFolderId = GoogleDriveUtils.createFolder(localDirToSend.getName(), remoteFolderId, sender);

            for (File file : localDirToSend.listFiles()) {
                sendFolder(file, remoteFolderId);
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
    public void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : tasks) {
            task.cancel(true);
        }
    }

    private static class GoogleDriveUploadProgressListener implements MediaHttpUploaderProgressListener {

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
