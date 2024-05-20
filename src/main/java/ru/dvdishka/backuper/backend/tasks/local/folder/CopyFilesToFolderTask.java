package ru.dvdishka.backuper.backend.tasks.local.folder;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.nio.file.Files;
import java.util.Objects;

public class CopyFilesToFolderTask extends Task {

    private static String taskName = "CopyFiles";

    private final File sourceDirToCopy;
    private final File targetDir;
    private boolean forceExcludedDirs = false;

    private volatile long completedCopyTasksCount = 0;
    // Doesn't have to be synchronized because all increasings come from the thread staring each copy thread, and this thread is only one
    private long copyTasksCount = 0;
    private boolean createRootDirInTargetDir = true;

    public CopyFilesToFolderTask(File sourceDirToCopy, File targetDir, boolean createRootDirInTargetDir, boolean forceExcludedDirs, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.sourceDirToCopy = sourceDirToCopy;
        this.targetDir = targetDir;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try {
            Logger.getLogger().devLog("CopyFiles task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            copyTasksCount = 0;
            completedCopyTasksCount = 0;

            if (createRootDirInTargetDir) {
                unsafeCopyFilesInDir(targetDir.toPath().resolve(sourceDirToCopy.getName()).toFile(), sourceDirToCopy);
            } else {
                unsafeCopyFilesInDir(targetDir, sourceDirToCopy);
            }

            // Waiting for all files being copied
            while (completedCopyTasksCount < copyTasksCount) {}

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running CopyFiles task");
            Logger.getLogger().warn(this, e);
        }
    }

    private synchronized void taskCompleted() {
        completedCopyTasksCount++;
    }

    private void unsafeCopyFilesInDir(File destDir, File sourceDir) {

        if (!sourceDir.exists()) {
            Logger.getLogger().warn("Something went wrong while copying files from " + sourceDir.getAbsolutePath());
            Logger.getLogger().warn("Directory " + sourceDir.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(sourceDir, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (sourceDir.isFile() && !sourceDir.getName().equals("session.lock")) {

            copyTasksCount++;

            final long taskNumber = copyTasksCount;

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

                try {

                    Files.copy(sourceDir.toPath(), destDir.toPath());

                    taskCompleted();

                    incrementCurrentProgress(Files.size(sourceDir.toPath()));

                } catch (SecurityException e) {

                    Logger.getLogger().warn("Backup Directory is not allowed to modify! " + sourceDir.getName(), sender);
                    Logger.getLogger().warn("BackupTask", e);

                    taskCompleted();

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong while trying to copy file! " + sourceDir.getName(), sender);
                    Logger.getLogger().warn("BackupTask", e);

                    taskCompleted();
                }
            });
        }

        if (sourceDir.listFiles() != null) {

            if (!destDir.exists() && !destDir.mkdirs()) {
                Logger.getLogger().warn("Failed to create dir: " + destDir.getPath(), sender);
            }

            for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

                unsafeCopyFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);
            }
        }
    }

    @Override
    public void prepareTask() {

        isTaskPrepared = true;

        if (forceExcludedDirs) {
            this.maxProgress = Utils.getFileFolderByteSize(sourceDirToCopy);
        } else {
            this.maxProgress = Utils.getFileFolderByteSizeExceptExcluded(sourceDirToCopy);
        }
    }
}
