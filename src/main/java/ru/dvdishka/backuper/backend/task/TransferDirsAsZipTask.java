package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.LocalStorage;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TransferDirsAsZipTask extends BaseTask implements DoubleStorageTask {

    private static final int FILE_BUFFER_SIZE = 65536;
    private static final int STREAM_BUFFER_SIZE = 1048576;
    private static final int PIPE_BUFFER_SIZE = 4194304;

    private final Storage sourceStorage;
    private final List<String> sourceDirs;
    private final boolean forceExcludedDirs;
    private final boolean createRootDirInTargetZIP;
    private final Storage targetStorage;
    private final String targetParentDir;
    private final String targetZipFileName;

    private final Collection<StorageProgressListener> downloadProgressListeners = new ConcurrentLinkedQueue<>();
    private final AtomicLong bytesUploaded = new AtomicLong(0);

    private volatile PipedInputStream activePipe;

    /***
     * @param sourceDirs Absolute paths. Don't try to add there a file you want to send without createRootDirInTargetZIP a true option
     * @param targetParentDir Absolute path
     */
    public TransferDirsAsZipTask(Storage sourceStorage, List<String> sourceDirs, Storage targetStorage, String targetParentDir, String targetZipFileName,
                           boolean createRootDirInTargetZIP, boolean forceExcludedDirs) {

        this.sourceStorage = sourceStorage;
        this.targetStorage = targetStorage;
        this.sourceDirs = sourceDirs;
        this.targetParentDir = targetParentDir;
        this.targetZipFileName = targetZipFileName;
        this.createRootDirInTargetZIP = createRootDirInTargetZIP;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() {
        TaskManager taskManager = Backuper.getInstance().getTaskManager();
        CompletableFuture<Void> writer = null;
        Throwable failure = null;
        try (PipedInputStream input = new PipedInputStream(PIPE_BUFFER_SIZE);
             PipedOutputStream output = new PipedOutputStream(input)) {
            activePipe = input;
            checkCancelled();
            writer = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                try {
                    taskManager.registerCurrentThread(this);
                    try (BufferedOutputStream buffered = new BufferedOutputStream(output, STREAM_BUFFER_SIZE);
                         ZipOutputStream zip = new ZipOutputStream(buffered)) {
                        zip.setLevel(targetStorage.getConfig().getZipCompressionLevel());
                        for (String dir : sourceDirs) {
                            checkCancelled();
                            addDirToZip(zip, dir, createRootDirInTargetZIP ? sourceStorage.getFileNameFromPath(dir) : "");
                        }
                        checkCancelled();
                    } finally {
                        taskManager.unregisterCurrentThread(this);
                    }
                } catch (Exception e) {
                    throw new CompletionException(e);
                } finally {
                    // Also signal EOF when cancellation happens before the ZIP stream is constructed.
                    try { output.close(); } catch (IOException ignored) { }
                }
            });
            targetStorage.uploadFile(input, targetZipFileName, targetParentDir);
            checkCancelled();
        } catch (Throwable e) {
            failure = e;
            taskManager.interruptRunningThreads(this);
        } finally {
            activePipe = null;
            if (writer != null) {
                try {
                    // Completion means the writer has really exited, including closing its source stream.
                    writer.join();
                } catch (CompletionException e) {
                    if (failure == null) failure = e.getCause();
                    else failure.addSuppressed(e.getCause());
                }
            }
        }
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new CompletionException(failure);
    }

    @Override
    public long getTaskCurrentProgress() {
        return downloadProgressListeners.stream().mapToLong(StorageProgressListener::getCurrentProgress).sum();
    }

    @Override
    public void prepareTask(CommandSender sender) {
        if (maxProgress != 0) return;
        if (sourceStorage instanceof LocalStorage && !forceExcludedDirs) {
            for (String dir : sourceDirs) {
                this.maxProgress += Utils.getFileFolderByteSizeExceptExcluded(new File(dir));
            }
        } else {
            for (String dir : sourceDirs) {
                this.maxProgress += sourceStorage.getDirByteSize(dir);
            }
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        PipedInputStream pipe = activePipe;
        if (pipe != null) {
            try { pipe.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Recursively add a directory (or file) into the ZIP output stream.
     */
    private void addDirToZip(ZipOutputStream zip, String sourceDir, String relativeDirPath) throws IOException {
        checkCancelled();
        if (!sourceStorage.exists(sourceDir)) {
            warn("Directory does not exist: %s".formatted(sourceDir), sender);
            return;
        }
        if (sourceStorage instanceof LocalStorage && !forceExcludedDirs && Utils.isExcludedDirectory(new File(sourceDir), sender)) return;
        try {
            if (sourceStorage.isFile(sourceDir)) {

                long crc = 0;
                if (isAlreadyCompressed(sourceStorage, sourceDir)) {
                    crc = calculateCRC(sourceDir);
                }

                StorageProgressListener downloadProgressListener = new BasicStorageProgressListener();
                downloadProgressListeners.add(downloadProgressListener);
                try (InputStream directInputStream = sourceStorage.downloadFile(sourceDir, downloadProgressListener);
                     BufferedInputStream bufferedInputStream = new BufferedInputStream(directInputStream, FILE_BUFFER_SIZE)) {

                    ZipEntry entry = new ZipEntry(relativeDirPath);
                    if (isAlreadyCompressed(sourceStorage, sourceDir)) {
                        entry.setMethod(ZipEntry.STORED);
                        entry.setCompressedSize(sourceStorage.getDirByteSize(sourceDir));
                        entry.setCrc(crc);
                    }
                    entry.setSize(sourceStorage.getDirByteSize(sourceDir));
                    zip.putNextEntry(entry);
                    byte[] buffer = new byte[FILE_BUFFER_SIZE];
                    int read;
                    while ((read = bufferedInputStream.read(buffer)) != -1) {
                        checkCancelled();
                        zip.write(buffer, 0, read);
                        bytesUploaded.getAndAdd(read);
                    }
                    zip.closeEntry();
                } finally {
                    sourceStorage.downloadCompleted();
                }
            }
        } catch (Exception e) {
            warn("Error adding to ZIP: %s".formatted(sourceDir), sender);
            warn(e);
        }
        if (sourceStorage.isDir(sourceDir)) {
            try {
                if (!relativeDirPath.isEmpty()) {
                    ZipEntry entry = new ZipEntry(relativeDirPath.endsWith("/") ? relativeDirPath : relativeDirPath + "/");
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                }

                List<String> ls = sourceStorage.ls(sourceDir);
                for (String file : ls) {
                    if (!"session.lock".equals(file)) {
                        addDirToZip(zip, sourceStorage.resolve(sourceDir, file), relativeDirPath.isEmpty() ? file : relativeDirPath + "/" + file);
                    }
                }
            } catch (Exception e) {
                warn("Error adding a dir to ZIP: %s".formatted(sourceDir), sender);
                warn(e);
            }
        }
    }

    /**
     * Whether the file should be stored without compression.
     */
    private boolean isAlreadyCompressed(Storage storage, String path) {
        String name = storage.getFileNameFromPath(path).toLowerCase();
        return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".gz")
                || name.endsWith(".7z") || name.endsWith(".rar")
                || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".mp3")
                || name.endsWith(".mp4") || name.endsWith(".avi")
                || name.endsWith(".mkv") || name.endsWith(".webm")
                || name.endsWith(".webp");
    }

    /**
     * Calculate CRC for a file (required for STORED method).
     */
    private long calculateCRC(String path) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(
                sourceStorage.downloadFile(path), FILE_BUFFER_SIZE)) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                checkCancelled();
                crc.update(buffer, 0, read);
            }
            return crc.getValue();
        } finally {
            sourceStorage.downloadCompleted();
        }
    }

    public long getBytesUploaded() {
        return bytesUploaded.get();
    }

    @Override
    public Storage getSourceStorage() {
        return sourceStorage;
    }

    @Override
    public Storage getTargetStorage() {
        return targetStorage;
    }
}
