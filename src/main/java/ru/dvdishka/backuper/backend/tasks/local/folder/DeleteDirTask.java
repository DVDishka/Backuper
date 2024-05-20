package ru.dvdishka.backuper.backend.tasks.local.folder;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.nio.file.Files;
import java.util.Objects;

public class DeleteDirTask extends Task {

    private static final String taskName = "DeleteDir";

    private final File dirToDelete;

    public DeleteDirTask(File dirToDelete, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.dirToDelete = dirToDelete;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {

            Logger.getLogger().devLog("DeleteDir task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            deleteDir(dirToDelete);

            Logger.getLogger().devLog("DeleteDir task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running DeleteDirTask", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    private void deleteDir(File dir) {

        if (!dir.exists()) {
            Logger.getLogger().warn("Directory " + dir.getAbsolutePath() + " does not exist");
            return;
        }

        if (dir.isFile()) {

            long fileByteSize = 0;

            try {
                fileByteSize = Files.size(dir.toPath());
            } catch (Exception e) {
                Logger.getLogger().warn("Failed to get file size before deletion", sender);
                Logger.getLogger().warn(DeleteDirTask.class, e);
            }

            if (!dir.delete()) {

                Logger.getLogger().warn("Can not delete file " + dir.getName(), sender);
            }

            incrementCurrentProgress(fileByteSize);
        }

        else if (dir.isDirectory()) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                deleteDir(file);
            }
            if (!dir.delete()) {

                Logger.getLogger().warn("Can not delete directory " + dir.getName(), sender);
            }
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;
        maxProgress = Utils.getFileFolderByteSize(dirToDelete);
    }
}
