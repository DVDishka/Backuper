package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Abstract base for zipping local directories. Implements common traversal, CRC
 * calculation, and progress preparation.
 */
public abstract class BaseAddLocalDirToZipTask extends BaseAsyncTask {
    protected static final int FILE_BUFFER_SIZE = 65536; // 64KB buffer
    protected final List<File> sourceDirsToAdd;
    protected final boolean forceExcludedDirs;
    protected final boolean createRootDirInTargetZIP;

    protected BaseAddLocalDirToZipTask(List<File> sourceDirsToAdd,
                                       boolean createRootDirInTargetZIP,
                                       boolean forceExcludedDirs) {
        this.sourceDirsToAdd = sourceDirsToAdd;
        this.forceExcludedDirs = forceExcludedDirs;
        this.createRootDirInTargetZIP = createRootDirInTargetZIP;
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        if (!forceExcludedDirs) {
            for (File dir : sourceDirsToAdd) {
                this.maxProgress += Utils.getFileFolderByteSizeExceptExcluded(dir);
            }
        } else {
            for (File dir : sourceDirsToAdd) {
                this.maxProgress += Utils.getFileFolderByteSize(dir);
            }
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;
    }

    /**
     * Recursively add a directory (or file) into the ZIP output stream.
     */
    protected void addDirToZip(ZipOutputStream zip, File sourceDir, Path relativeDirPath) {
        if (cancelled) {
            return;
        }
        if (!sourceDir.exists()) {
            warn("Directory does not exist: " + sourceDir.getAbsolutePath(), sender);
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
                        zip.setLevel(getZipCompressionLevel());
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
            warn("Error adding to ZIP: " + sourceDir.getName(), sender);
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
    protected boolean isAlreadyCompressed(File file) {
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
    protected long calculateCRC(File file) throws IOException {
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

    /**
     * Child classes must provide the ZIP compression level for non-stored entries.
     */
    protected abstract int getZipCompressionLevel();
}
