package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.SftpUtils;
import ru.dvdishka.backuper.backend.util.Utils;

import javax.naming.AuthenticationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class UploadDirTask extends BaseTask implements SingleStorageTask {

    private final Storage targetStorage;
    private final File sourceDir;
    private String targetDir;
    private final boolean createRootDirInTargetDir;
    private final boolean forceExcludedDirs;

    private final ArrayList<CompletableFuture<Void>> jobs = new ArrayList<>();
    private ArrayList<StorageProgressListener> progressListeners;
    private long dirSize = 0;

    public UploadDirTask(Storage targetStorage, File sourceDir, String targetDir, boolean createRootDirInTargetDir, boolean forceExcludedDirs) {
        super();

        this.targetStorage = targetStorage;
        this.sourceDir = sourceDir;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.targetDir = targetDir;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() throws IOException, JSchException, SftpException {

        if (createRootDirInTargetDir) {
            targetStorage.createDir(sourceDir.getName(), targetDir);
            targetDir = targetStorage.resolve(targetDir, sourceDir.getName());
        }

        progressListeners = new ArrayList<>();
        if (!cancelled) {
            sendFolder(sourceDir, targetDir);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws ExecutionException, InterruptedException, AuthenticationException, IOException, Storage.StorageLimitException, Storage.StorageQuotaExceededException, SftpException {
        if (forceExcludedDirs) {
            dirSize = Utils.getFileFolderByteSize(sourceDir);
        } else {
            dirSize = Utils.getFileFolderByteSizeExceptExcluded(sourceDir);
        }
    }

    private void sendFolder(File sourceDir, String targetPath) {

        if (cancelled) {
            return;
        }

        if (!sourceDir.exists()) {
            warn("Something went wrong while trying to send files from %s".formatted(sourceDir.getAbsolutePath()));
            warn("Directory %s does not exist".formatted(sourceDir.getAbsolutePath()), sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(sourceDir, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (sourceDir.isFile() && !sourceDir.getName().equals("session.lock")) {

            try {
                StorageProgressListener progressListener = new BasicStorageProgressListener();
                progressListeners.add(progressListener);
                CompletableFuture<Void> job = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try {
                        targetStorage.uploadFile(sourceDir, sourceDir.getName(), targetPath, progressListener);
                    } catch (Exception e) {
                        warn("Failed to send file \"%s\" to %s storage".formatted(sourceDir.getAbsolutePath(), targetStorage.getId()));
                        warn(e);
                    }
                });

                jobs.add(job);
                try {
                    job.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Failed to send file \"%s\" to %s storage".formatted(sourceDir.getAbsolutePath(), targetStorage.getId()), sender);
                        warn(e);
                    }
                }

            } catch (Exception e) {
                warn("Something went wrong while sending file to the SFTP channel", sender);
                warn(e);
            }
        }
        if (sourceDir.isDirectory() && sourceDir.listFiles() != null) {
            for (File file : sourceDir.listFiles()) {
                if (file.isDirectory()) {
                    targetStorage.createDir(file.getName(), targetPath);
                }
                sendFolder(file, SftpUtils.resolve(targetPath, file.getName()));
            }
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return maxProgress;
        }

        if (progressListeners == null) {
            return 0;
        }

        long currentProgress = 0;
        for (StorageProgressListener progressListener : progressListeners) {
            currentProgress += progressListener.getCurrentProgress();
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

        for (CompletableFuture<Void> job : jobs) {
            job.cancel(true);
        }
    }

    @Override
    public Storage getStorage() {
        return targetStorage;
    }
}
