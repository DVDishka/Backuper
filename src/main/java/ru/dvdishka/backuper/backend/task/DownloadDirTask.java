package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class DownloadDirTask extends BaseTask implements SingleStorageTask {

    private final Storage sourceStorage;
    private final String sourcePath;
    private File targetFile;
    private final boolean createRootDirInTargetDir;

    private final List<CompletableFuture<Void>> jobs = new ArrayList<>();
    private final List<StorageProgressListener> progressListeners = new ArrayList<>();

    private long sourceDirSize = 0;

    public DownloadDirTask(Storage sourceStorage, String sourcePath, File targetFile, boolean createRootDirInTarget) {
        this.sourceStorage = sourceStorage;
        this.sourcePath = sourcePath;
        this.targetFile = targetFile;
        this.createRootDirInTargetDir = createRootDirInTarget;
    }

    @Override
    public void run() throws IOException, JSchException, SftpException {

        if (createRootDirInTargetDir) {
            String remoteDirName = "";
            for (char c : sourcePath.toCharArray()) {

                String symbol = String.valueOf(c);

                if (Objects.equals(symbol, Config.getInstance().getSftpConfig().getPathSeparatorSymbol())) {
                    remoteDirName = "";
                } else {
                    remoteDirName += symbol;
                }
            }

            targetFile = targetFile.toPath().resolve(remoteDirName).toFile();
        }

        if (!cancelled) {
            downloadDir(sourcePath, targetFile);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) throws SftpException {
        sourceDirSize = sourceStorage.getDirByteSize(sourcePath);
    }

    private void downloadDir(String currentSourcePath, File currentTargetFile) {

        if (cancelled) {
            return;
        }

        try {
            if (sourceStorage.isFile(currentSourcePath)) {

                if (!currentTargetFile.createNewFile()) {
                    throw new RuntimeException("Failed to create \"%s\" file in local storage".formatted(currentTargetFile.getAbsolutePath()));
                }
                StorageProgressListener progressListener = new BasicStorageProgressListener();
                progressListeners.add(progressListener);

                CompletableFuture<Void> job = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try {
                        sourceStorage.downloadFile(currentSourcePath, currentTargetFile, progressListener);
                    } catch (Exception e) {
                        warn("Something went wrong when trying to download file \"%s\" from %s storage".formatted(currentSourcePath, sourceStorage.getId()));
                        warn(e);
                    }
                });
                jobs.add(job);

                try {
                    job.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Something went wrong when trying to download file \"%s\" from %s storage".formatted(currentSourcePath, sourceStorage.getId()), sender);
                        warn(e);
                    }
                }

            } else {

                currentTargetFile.mkdirs();
                for (String entry : sourceStorage.ls(currentSourcePath)) {
                    if (entry.equals(".") || entry.equals("..")) {
                        continue;
                    }

                    downloadDir(sourceStorage.resolve(currentSourcePath, entry), currentTargetFile.toPath().resolve(entry).toFile());
                }
            }

        } catch (Exception e) {
            warn("Failed to get file %s from %s storage".formatted(currentSourcePath, sourceStorage.getId()), sender);
            warn(e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        if (cancelled) {
            return sourceDirSize;
        }

        long currentProgress = 0;
        for (StorageProgressListener progressListener : progressListeners) {
            currentProgress += progressListener.getCurrentProgress();
        }
        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return sourceDirSize;
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
        return sourceStorage;
    }
}
