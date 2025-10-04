package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.LocalConfig;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class LocalStorage implements PathStorage {

    @Setter
    private String id = null;
    private final BackupManager backupManager;
    private final LocalConfig config;

    private final int FILE_BUFFER_SIZE = 65536;

    public LocalStorage(LocalConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    @Override
    public LocalConfig getConfig() {
        return config;
    }

    @Override
    public BackupManager getBackupManager() {
        return backupManager;
    }

    @Override
    public boolean checkConnection() {
        return checkConnection(null);
    }

    @Override
    public boolean checkConnection(CommandSender sender) {
        try {
            if (!config.isEnabled()) {
                Backuper.getInstance().getLogManager().warn("Local storage is disabled in config.yml", sender);
                return false;
            }

            File folder = new File(config.getBackupsFolder());
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    Backuper.getInstance().getLogManager().warn("Failed to create local backups folder: %s".formatted(config.getBackupsFolder()), sender);
                    return false;
                }
            }
            
            if (!folder.isDirectory()) {
                Backuper.getInstance().getLogManager().warn("Local backups folder is not a directory: %s".formatted(config.getBackupsFolder()), sender);
                return false;
            }
            
            if (!folder.canRead() || !folder.canWrite()) {
                Backuper.getInstance().getLogManager().warn("Local backups folder is not accessible (read/write permissions): %s".formatted(config.getBackupsFolder()), sender);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to check local storage connection", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        try {
            File directory = new File(path);
            if (!directory.exists() || !directory.isDirectory()) {
                throw new StorageMethodException(this, "Directory does not exist or is not a directory: %s".formatted(path));
            }
            
            File[] files = directory.listFiles();
            if (files == null) {
                throw new StorageMethodException(this, "Failed to list files in directory: %s".formatted(path));
            }
            
            List<String> fileNames = new ArrayList<>();
            for (File file : files) {
                fileNames.add(file.getName());
            }
            return fileNames;
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to get file list from dir \"%s\" using local storage".formatted(path), e);
        }
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        return new File(path).exists();
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        File file = new File(path);
        if (!file.exists()) {
            throw new StorageMethodException(this, "File \"%s\" does not exist".formatted(path));
        }
        return file.isFile();
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        try {
            File file = new File(path);
            if (!file.exists()) {
                throw new StorageMethodException(this, "File or directory does not exist: %s".formatted(path));
            }
            
            return Utils.getFileFolderByteSize(file);
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to get \"%s\" dir size using local storage".formatted(path), e);
        }
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        try {
            File folder = new File(resolve(parentDir, newDirName));
            if (folder.exists()) {
                if (!folder.isDirectory()) {
                    throw new StorageMethodException(this, "Path exists but is not a directory: %s".formatted(parentDir));
                }
                return; // Folder already exists
            }
            
            if (!folder.mkdirs()) {
                throw new StorageMethodException(this, "Failed to create directory: %s".formatted(parentDir));
            }
        } catch (Exception e) {
            throw new StorageMethodException(this, "Failed to create dir \"%s\" using local storage".formatted(parentDir), e);
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        File target = new File(resolve(targetParentDir, newFileName));

        try (OutputStream targetStream = new FileOutputStream(target)) {
            if (sourceStream.markSupported()) sourceStream.reset();
            byte[] buffer = new byte[FILE_BUFFER_SIZE];
            int read;
            while ((read = sourceStream.read(buffer)) != -1) {
                targetStream.write(buffer, 0, read);
                progressListener.incrementProgress(read);
            }
        } catch (IOException e) {
            throw new StorageMethodException(this, "Failed to copy stream to \"%s\" in %s storage".formatted(target.getAbsolutePath(), id), e);
        }
    }

    @Override
    public InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        File file = new File(sourcePath);
        if (!file.exists()) {
            throw new StorageMethodException(this, "Source file \"%s\" does not exist".formatted(sourcePath));
        }

        try {
            return new LocalStorageFileInputStream(file, progressListener);
        } catch (IOException e) {
            throw new StorageMethodException(this, "Failed to get file's \"%s\" input stream from \"%s\" storage".formatted(sourcePath, id), e);
        }
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        File file = new File(path);
        if (!file.delete()) {
            throw new StorageMethodException(this, "Failed to delete \"%s\" file/dir from local storage".formatted(path));
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        File sourceFile = new File(path);
        File targetFile = new File(resolve(getParentPath(path), newFileName));

        if (!sourceFile.exists())
            throw new StorageMethodException(this, "Source file does not exist: %s".formatted(path));
        if (targetFile.exists())
            throw new StorageMethodException(this, "Target file already exists: %s".formatted(newFileName));

        // Create parent directories if they don't exist
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new StorageMethodException(this, "Failed to create parent directory for: %s".formatted(newFileName));
            }
        }

        // Sometimes doesn't work so me need to retry
        for (int i = 0; i < 1000000; i++) {
            try {
                if (!sourceFile.renameTo(targetFile)) {
                    // Try using Files.move as fallback
                    Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                break;
            } catch (Exception e) {
                if (i == 999999) throw new StorageMethodException(this, "Failed to rename file \"%s\" to \"%s\" using local storage".formatted(path, newFileName), e);
            }
        }
    }

    @Override
    public int getStorageSpeedMultiplier() {
        return 1;
    }

    @Override
    public void destroy() {}

    @Override
    public void downloadCompleted() throws StorageMethodException, StorageConnectionException {
        // Для локального хранилища не требуется дополнительных действий
    }

    private static class LocalStorageFileInputStream extends FileInputStream {

        private final StorageProgressListener progressListener;

        LocalStorageFileInputStream(File file, StorageProgressListener progressListener) throws FileNotFoundException {
            super(file);
            this.progressListener = progressListener;
        }

        @Override
        public int read() throws IOException {
            int result = super.read();
            if (result != -1) {
                progressListener.incrementProgress(1);
            }
            return result;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int bytesRead = super.read(b);
            if (bytesRead > 0) {
                progressListener.incrementProgress(bytesRead);
            }
            return bytesRead;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = super.read(b, off, len);
            if (bytesRead > 0) {
                progressListener.incrementProgress(bytesRead);
            }
            return bytesRead;
        }
    }
}
