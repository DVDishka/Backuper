package ru.dvdishka.backuper.backend.tasks.local.folder;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class CopyFilesToFolderTask extends Task {

    private static String taskName = "CopyFiles";

    private final File sourceDirToCopy;
    private final File targetDir;
    private boolean forceExcludedDirs = false;
    private boolean createRootDirInTargetDir = true;

    private final ArrayList<CompletableFuture<Void>> copyTasks = new ArrayList<>();

    public CopyFilesToFolderTask(File sourceDirToCopy, File targetDir, boolean createRootDirInTargetDir, boolean forceExcludedDirs,
                                 boolean setLocked, List<Permissions> permission, CommandSender sender) {

        super(taskName, setLocked, permission, sender);
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

            if (!cancelled) {
                if (createRootDirInTargetDir) {
                    unsafeCopyFilesInDir(targetDir.toPath().resolve(sourceDirToCopy.getName()).toFile(), sourceDirToCopy);
                } else {
                    unsafeCopyFilesInDir(targetDir, sourceDirToCopy);
                }

                try {
                    CompletableFuture.allOf(copyTasks.toArray(new CompletableFuture[0])).join();
                } catch (Exception e) {
                    Logger.getLogger().warn("Failed to copy files into target dir", sender);
                    Logger.getLogger().warn(this.getClass(), e);
                }
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

            Logger.getLogger().warn("Something went wrong while running CopyFiles task");
            Logger.getLogger().warn(this.getClass(), e);
        }
    }

    private void unsafeCopyFilesInDir(File destDir, File sourceDir) {

        if (cancelled) {
            return;
        }

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

        if (!cancelled && sourceDir.isFile() && !sourceDir.getName().equals("session.lock")) {

            CompletableFuture<Void> copyTask = CompletableFuture.runAsync(() -> {

                try {

                    Files.copy(sourceDir.toPath(), destDir.toPath());

                    incrementCurrentProgress(Files.size(sourceDir.toPath()));

                } catch (SecurityException e) {

                    Logger.getLogger().warn("Backup Directory is not allowed to modify! " + sourceDir.getName(), sender);
                    Logger.getLogger().warn("BackupTask", e);

                } catch (Exception e) {

                    Logger.getLogger().devWarn(this.getClass(), "Something went wrong while trying to copy file! " + sourceDir.getName());
                    Logger.getLogger().devWarn(this.getClass(), Arrays.toString(e.getStackTrace()));
                }
            });

            copyTasks.add(copyTask);
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

    @Override
    public void cancel() {

        cancelled = true;

        for (CompletableFuture<Void> task : copyTasks) {
            task.cancel(true);
        }

        currentProgress = maxProgress;
    }
}
