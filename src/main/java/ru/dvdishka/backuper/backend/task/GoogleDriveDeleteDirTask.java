package ru.dvdishka.backuper.backend.task;

import com.google.api.services.drive.model.File;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.exception.StorageQuotaExceededException;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;

import javax.naming.AuthenticationException;
import java.io.IOException;

public class GoogleDriveDeleteDirTask extends BaseAsyncTask {

    private final String driveFileId;

    public GoogleDriveDeleteDirTask(String driveFileId) {
        super();

        this.driveFileId = driveFileId;
    }

    @Override
    protected void run() {
        try {

            if (!cancelled) {
                deleteDir(driveFileId);
            }

        } catch (Exception e) {
            warn("Something went wrong when trying to execute " + taskName + " task", sender);
            warn(e);
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) throws AuthenticationException, IOException, StorageLimitException, StorageQuotaExceededException {
        maxProgress = GoogleDriveUtils.getFileByteSize(driveFileId);
    }

    @Override
    protected void cancel() {
        cancelled = true;
    }

    private void deleteDir(String currentDriveFileId) {

        if (cancelled) {
            return;
        }

        try {
            if (GoogleDriveUtils.isFolder(currentDriveFileId)) {

                for (File file : GoogleDriveUtils.ls(currentDriveFileId)) {
                    deleteDir(file.getId());
                }
                GoogleDriveUtils.deleteFile(currentDriveFileId);
            }
            else {
                long fileSize = GoogleDriveUtils.getFileByteSize(currentDriveFileId);
                GoogleDriveUtils.deleteFile(currentDriveFileId);
                incrementCurrentProgress(fileSize);
            }
        } catch (Exception e) {
            warn("Something went while trying to delete file %s from Google Drive".formatted(currentDriveFileId), sender);
            warn(e);
        }
    }
}
