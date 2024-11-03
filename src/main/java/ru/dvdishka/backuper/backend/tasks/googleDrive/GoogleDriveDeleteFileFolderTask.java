package ru.dvdishka.backuper.backend.tasks.googleDrive;

import com.google.api.services.drive.model.File;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class GoogleDriveDeleteFileFolderTask extends Task {

    private static final String taskName = "GoogleDriveDeleteFileFolder";

    private final String driveFileId;

    public GoogleDriveDeleteFileFolderTask(String driveFileId, boolean setLocked, List<Permissions> permissions, CommandSender sender) {
        super(taskName, setLocked, permissions, sender);

        this.driveFileId = driveFileId;
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

            Logger.getLogger().devLog(taskName + " task started");

            if (!cancelled) {
                deleteDir(driveFileId);
            }

            if (setLocked) {
                Backuper.unlock();
                UIUtils.successSound(sender);
            }

        } catch (Exception e) {
            if (setLocked) {
                Backuper.unlock();
                UIUtils.cancelSound(sender);
            }
            Logger.getLogger().warn("Something went wrong when trying to execute " + taskName + " task", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        maxProgress = GoogleDriveUtils.getFileByteSize(driveFileId, sender);
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    private void deleteDir(String currentDriveFileId) {

        if (cancelled) {
            return;
        }

        try {
            if (GoogleDriveUtils.isFolder(currentDriveFileId, sender)) {

                for (File file : GoogleDriveUtils.ls(currentDriveFileId, sender)) {
                    deleteDir(file.getId());
                }
                GoogleDriveUtils.deleteFile(currentDriveFileId, sender);
            }
            else {
                long fileSize = GoogleDriveUtils.getFileByteSize(currentDriveFileId, sender);
                GoogleDriveUtils.deleteFile(currentDriveFileId, sender);
                incrementCurrentProgress(fileSize);
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Something went while trying to delete FTP(S) directory", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }
}
