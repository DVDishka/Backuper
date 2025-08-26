package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.StorageProgressListener;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class UploadDirsAsZip extends BaseTask implements SingleStorageTask {

    private static final int FILE_BUFFER_SIZE = 65536;
    private static final int STREAM_BUFFER_SIZE = 1048576;
    private static final int PIPE_BUFFER_SIZE = 4194304;

    private final List<File> sourceDirs;
    private final boolean forceExcludedDirs;
    private final boolean createRootDirInTargetZIP;
    private final Storage targetStorage;
    private final String targetParentDir;
    private final String targetZipFileName;

    public UploadDirsAsZip(Storage targetStorage, List<File> sourceDirs, String targetParentDir, String targetZipFileName,
                           boolean createRootDirInTargetZIP, boolean forceExcludedDirs) {

        this.targetStorage = targetStorage;
        this.sourceDirs = sourceDirs;
        this.targetParentDir = targetParentDir;
        this.targetZipFileName = targetZipFileName;
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

                    for (File sourceDirToAdd : sourceDirs) {

                        if (cancelled) {
                            break;
                        }

                        if (createRootDirInTargetZIP) {
                            File parent = sourceDirToAdd.getParentFile();
                            parent = parent == null ? new File("") : parent;
                            addDirToZip(targetZipOutputStream, sourceDirToAdd, parent.toPath());
                        } else {
                            addDirToZip(targetZipOutputStream, sourceDirToAdd, sourceDirToAdd.toPath());
                        }
                    }

                } catch (Exception e) {
                    warn("Failed to send ZIP entry to %s storage".formatted(targetStorage), sender);
                    warn(e);
                }
            });

            final StorageProgressListener progressListener = new BasicStorageProgressListener();
            targetStorage.uploadFile(pipedInputStream, targetZipFileName, targetParentDir, progressListener);
        }
    }

    @Override
    public void prepareTask(CommandSender sender) {
        if (!forceExcludedDirs) {
            for (File dir : sourceDirs) {
                this.maxProgress += Utils.getFileFolderByteSizeExceptExcluded(dir);
            }
        } else {
            for (File dir : sourceDirs) {
                this.maxProgress += Utils.getFileFolderByteSize(dir);
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
    private void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {
        if (cancelled) {
            return;
        }
        if (!sourceDir.exists()) {
            warn("Directory does not exist: %s".formatted(sourceDir.getAbsolutePath()), sender);
            return;
        }
        boolean excluded = Utils.isExcludedDirectory(sourceDir, sender);
        if (excluded && !forceExcludedDirs) {
            return;
        }
        try {
            if (sourceDir.isFile()) {
                try (BufferedInputStream bis = new BufferedInputStream(
                        new FileInputStream(sourceDir), FILE_BUFFER_SIZE)) {
                    String relativePath = relativeDirPath.toAbsolutePath()
                            .relativize(sourceDir.toPath().toAbsolutePath()).toString();
                    ZipEntry entry = new ZipEntry(relativePath);
                    if (isAlreadyCompressed(sourceDir)) {
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
                        if (cancelled)
                            break;
                        zip.write(buffer, 0, read);
                        incrementCurrentProgress(read);
                    }
                    zip.closeEntry();
                }
            }
        } catch (Exception e) {
            warn("Error adding to ZIP: %s".formatted(sourceDir.getName()), sender);
            warn(e);
        }
        File[] children = sourceDir.listFiles();
        if (children != null) {
            for (File f : children) {
                if (!"session.lock".equals(f.getName())) {
                    addDirToZip(zip, f, relativeDirPath);
                }
            }
        }
    }

    /**
     * Whether the file should be stored without compression.
     */
    private boolean isAlreadyCompressed(File file) {
        String name = file.getName().toLowerCase();
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
    private long calculateCRC(File file) throws IOException {
        try (BufferedInputStream bis = new BufferedInputStream(
                new FileInputStream(file), FILE_BUFFER_SIZE)) {
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
    public Storage getStorage() {
        return targetStorage;
    }
}