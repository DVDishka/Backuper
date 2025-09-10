package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.LocalStorage;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.*;
import java.util.List;
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

    private StorageProgressListener progressListener;

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
        this.targetZipFileName = "%s in progress".formatted(targetZipFileName);
        this.createRootDirInTargetZIP = createRootDirInTargetZIP;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    public void run() throws IOException {

        try (PipedInputStream pipedInputStream = new PipedInputStream(PIPE_BUFFER_SIZE);
             PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {

            Backuper.getInstance().getScheduleManager().runAsync(() -> {

                // Use BufferedOutputStream for better performance
                try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(pipedOutputStream,
                        STREAM_BUFFER_SIZE);
                     ZipOutputStream targetZipOutputStream = new ZipOutputStream(bufferedOutputStream)) {

                    for (String sourceDirToAdd : sourceDirs) {

                        if (cancelled) {
                            break;
                        }

                        if (createRootDirInTargetZIP) {
                            addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceStorage.getFileNameFromPath(sourceDirToAdd));
                        } else {
                            addDirToZip(targetZipOutputStream, sourceDirToAdd, "");
                        }
                    }

                } catch (Exception e) {
                    warn("Failed to send ZIP entry to %s storage".formatted(targetStorage), sender);
                    warn(e);
                }
            });

            progressListener = new BasicStorageProgressListener();
            targetStorage.uploadFile(pipedInputStream, targetZipFileName, targetParentDir, progressListener);
            targetStorage.renameFile(targetStorage.resolve(targetParentDir, targetZipFileName), targetZipFileName.replace(" in progress", ""));
        }
    }

    @Override
    public long getTaskCurrentProgress() {
        return progressListener.getCurrentProgress();
    }

    @Override
    public void prepareTask(CommandSender sender) {
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
        cancelled = true;
    }

    /**
     * Recursively add a directory (or file) into the ZIP output stream.
     */
    private void addDirToZip(ZipOutputStream zip, String sourceDir, String relativeDirPath) {
        if (cancelled) return;
        if (!sourceStorage.exists(sourceDir)) {
            warn("Directory does not exist: %s".formatted(sourceDir), sender);
            return;
        }
        if (sourceStorage instanceof LocalStorage && !forceExcludedDirs && Utils.isExcludedDirectory(new File(sourceDir), sender)) return;
        try {
            if (sourceStorage.isFile(sourceDir)) {
                try (BufferedInputStream bis = new BufferedInputStream(
                        new FileInputStream(sourceDir), FILE_BUFFER_SIZE)) {

                    ZipEntry entry = new ZipEntry(relativeDirPath);
                    if (isAlreadyCompressed(sourceStorage, sourceDir)) {
                        entry.setMethod(ZipEntry.STORED);
                        entry.setSize(sourceDir.length());
                        entry.setCompressedSize(sourceDir.length());
                        entry.setCrc(calculateCRC(sourceDir));
                    } else {
                        zip.setLevel(targetStorage.getConfig().getZipCompressionLevel());
                    }
                    zip.putNextEntry(entry);
                    byte[] buffer = new byte[FILE_BUFFER_SIZE];
                    int read;
                    while ((read = bis.read(buffer)) != -1) {
                        if (cancelled) break;
                        zip.write(buffer, 0, read);
                        incrementCurrentProgress(read);
                    }
                    zip.closeEntry();
                }
            }
        } catch (Exception e) {
            warn("Error adding to ZIP: %s".formatted(sourceDir), sender);
            warn(e);
        }
        if (sourceStorage.isDir(sourceDir)) {
            try {
                ZipEntry entry = new ZipEntry(relativeDirPath);
                zip.putNextEntry(entry);
                zip.closeEntry();

                List<String> ls = sourceStorage.ls(sourceDir);
                for (String file : ls) {
                    if (!"session.lock".equals(file)) {
                        addDirToZip(zip, sourceStorage.resolve(sourceDir, file), "%s/%s".formatted(relativeDirPath, file));
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
                sourceStorage.downloadFile(path, new BasicStorageProgressListener()), FILE_BUFFER_SIZE)) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int read;
            while ((read = bis.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
            }
            return crc.getValue();
        }
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
