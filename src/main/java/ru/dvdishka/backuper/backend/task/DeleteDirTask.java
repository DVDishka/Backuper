package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.nio.file.Files;
import java.util.Objects;

public class DeleteDirTask extends BaseAsyncTask {

    private final File dirToDelete;

    public DeleteDirTask(File dirToDelete) {

        super();
        this.dirToDelete = dirToDelete;
    }

    @Override
    protected void run() {
        deleteDir(dirToDelete);
    }

    private void deleteDir(File dir) {

        if (cancelled) {
            return;
        }

        if (!dir.exists()) {
            warn("Directory " + dir.getAbsolutePath() + " does not exist");
            return;
        }

        if (!cancelled && dir.isFile()) {

            long fileByteSize = 0;

            try {
                fileByteSize = Files.size(dir.toPath());
            } catch (Exception e) {
                warn("Failed to get file size before deletion", sender);
                warn(e);
            }

            if (!dir.delete()) {

                warn("Can not delete file " + dir.getName(), sender);
            }

            incrementCurrentProgress(fileByteSize);
        } else if (dir.isDirectory()) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                deleteDir(file);
            }
            if (!dir.delete()) {

                warn("Can not delete directory " + dir.getName(), sender);
            }
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        maxProgress = Utils.getFileFolderByteSize(dirToDelete);
    }

    protected void cancel() {
        cancelled = true;
    }
}
