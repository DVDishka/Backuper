package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class CopyDirTask extends BaseAsyncTask {

    private final File sourceDirToCopy;
    private final File targetDir;
    private final boolean forceExcludedDirs;
    private final boolean createRootDirInTargetDir;

    private final ArrayList<CompletableFuture<Void>> copyTasks = new ArrayList<>();

    public CopyDirTask(File sourceDirToCopy, File targetDir, boolean createRootDirInTargetDir, boolean forceExcludedDirs) {

        super();
        this.sourceDirToCopy = sourceDirToCopy;
        this.targetDir = targetDir;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    protected void run() {

        if (!cancelled) {
            if (createRootDirInTargetDir) {
                unsafeCopyFilesInDir(targetDir.toPath().resolve(sourceDirToCopy.getName()).toFile(), sourceDirToCopy);
            } else {
                unsafeCopyFilesInDir(targetDir, sourceDirToCopy);
            }

            try {
                CompletableFuture.allOf(copyTasks.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                warn("Failed to copy files into target dir", sender);
                warn(e);
            }
        }
    }

    private void unsafeCopyFilesInDir(File destDir, File sourceDir) {

        if (cancelled) {
            return;
        }

        if (!sourceDir.exists()) {
            warn("Something went wrong while copying files from " + sourceDir.getAbsolutePath());
            warn("Directory " + sourceDir.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(sourceDir, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (!cancelled && sourceDir.isFile() && !sourceDir.getName().equals("session.lock")) {

            CompletableFuture<Void> copyTask = Backuper.getInstance().getScheduleManager().runAsync(() -> {

                try {

                    Files.copy(sourceDir.toPath(), destDir.toPath());

                    incrementCurrentProgress(Files.size(sourceDir.toPath()));

                } catch (SecurityException e) {

                    warn("Backup Directory is not allowed to modify! " + sourceDir.getName(), sender);
                    warn(e);

                } catch (Exception e) {

                    devWarn("Something went wrong while trying to copy file! " + sourceDir.getName());
                    devWarn(Arrays.toString(e.getStackTrace()));
                }
            });

            copyTasks.add(copyTask);
        }

        if (sourceDir.listFiles() != null) {

            if (!destDir.exists() && !destDir.mkdirs()) {
                warn("Failed to create dir: " + destDir.getPath(), sender);
            }

            for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

                unsafeCopyFilesInDir(destDir.toPath().resolve(file.getName()).toFile(), file);
            }
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {

        if (forceExcludedDirs) {
            this.maxProgress = Utils.getFileFolderByteSize(sourceDirToCopy);
        } else {
            this.maxProgress = Utils.getFileFolderByteSizeExceptExcluded(sourceDirToCopy);
        }
    }

    @Override
    protected void cancel() {

        cancelled = true;

        for (CompletableFuture<Void> task : copyTasks) {
            task.cancel(true);
        }
    }
}
