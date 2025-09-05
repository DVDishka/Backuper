package ru.dvdishka.backuper.backend.storage;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.LocalConfig;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class LocalStorage implements Storage {

    private String id = null;
    private final BackupManager backupManager;
    private final LocalConfig config;

    public LocalStorage(LocalConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public Backup.StorageType getType() {
        return Backup.StorageType.LOCAL;
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
                throw new StorageMethodException("Directory does not exist or is not a directory: %s".formatted(path));
            }
            
            File[] files = directory.listFiles();
            if (files == null) {
                throw new StorageMethodException("Failed to list files in directory: %s".formatted(path));
            }
            
            List<String> fileNames = new ArrayList<>();
            for (File file : files) {
                fileNames.add(file.getName());
            }
            return fileNames;
        } catch (Exception e) {
            throw new StorageMethodException("Failed to get file list from dir \"%s\" using local storage".formatted(path), e);
        }
    }

    @Override
    public String resolve(String path, String fileName) {
        if (!path.endsWith(config.getPathSeparatorSymbol())) {
            path = "%s%s".formatted(path, config.getPathSeparatorSymbol());
        }
        return "%s%s".formatted(path, fileName);
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        return new File(path).exists();
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        File file = new File(path);
        if (!file.exists()) {
            throw new StorageMethodException("File \"%s\" does not exist");
        }
        return file.isFile();
    }

    @Override
    public String getFileNameFromPath(String path) throws StorageMethodException, StorageConnectionException {
        return path.substring(path.lastIndexOf(config.getPathSeparatorSymbol()) + 1);
    }

    @Override
    public String getParentPath(String path) throws StorageMethodException, StorageConnectionException {
        return path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
    }

    @Override
    public long getDirByteSize(String remoteFilePath) throws StorageMethodException, StorageConnectionException {
        try {
            File file = new File(remoteFilePath);
            if (!file.exists()) {
                throw new StorageMethodException("File or directory does not exist: %s".formatted(remoteFilePath));
            }
            
            return Utils.getFileFolderByteSize(file);
        } catch (Exception e) {
            throw new StorageMethodException("Failed to get \"%s\" dir size using local storage".formatted(remoteFilePath), e);
        }
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        try {
            File folder = new File(resolve(parentDir, newDirName));
            if (folder.exists()) {
                if (!folder.isDirectory()) {
                    throw new StorageMethodException("Path exists but is not a directory: %s".formatted(parentDir));
                }
                return; // Folder already exists
            }
            
            if (!folder.mkdirs()) {
                throw new StorageMethodException("Failed to create directory: %s".formatted(parentDir));
            }
        } catch (Exception e) {
            throw new StorageMethodException("Failed to create dir \"%s\" using local storage".formatted(parentDir), e);
        }
    }

    @Override
    public void uploadFile(File sourceFile, String newFileName, String remoteParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        if (!sourceFile.exists()) {
            throw new StorageMethodException("Source file \"%s\" does not exist");
        }

        File target = new File(resolve(remoteParentDir, newFileName));

        try {
            Files.copy(sourceFile.toPath(), target.toPath());
            progressListener.incrementProgress(sourceFile.length());
        } catch (IOException e) {
            throw new StorageMethodException("Failed to copy file from \"%s\" to \"%s\" in local storage".formatted(sourceFile.getAbsolutePath(), target.getAbsolutePath()));
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String remoteParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        File target = new File(resolve(remoteParentDir, newFileName));

        try {
            Files.copy(sourceStream, target.toPath());
            progressListener.incrementProgress(target.length());
        } catch (IOException e) {
            throw new StorageMethodException("Failed to copy stream to \"%s\" in local storage".formatted(target.getAbsolutePath()));
        }
    }

    @Override
    public void downloadFile(String sourceFile, File targetFile, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        File file = new File(sourceFile);
        if (!file.exists()) {
            throw new StorageMethodException("Source file \"%s\" does not exist".formatted(sourceFile));
        }

        try {
            Files.copy(file.toPath(), targetFile.toPath());
            progressListener.incrementProgress(file.length());
        } catch (IOException e) {
            throw new StorageMethodException("Failed to copy file from \"%s\" to \"%s\" in local storage".formatted(file.getAbsolutePath(), targetFile.getAbsolutePath()));
        }
    }

    @Override
    public InputStream downloadFile(String remotePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        File file = new File(remotePath);
        if (!file.exists()) {
            throw new StorageMethodException("Source file \"%s\" does not exist".formatted(remotePath));
        }

        try {
            return new FileInputStream(file);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get file's \"%s\" input stream from \"%s\" local storage".formatted(remotePath, this.getId()));
        }
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        File file = new File(path);
        if (!file.delete()) {
            throw new StorageMethodException("Failed to delete \"%s\" file/dir from local storage".formatted(path));
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        try {
            File sourceFile = new File(path);

            String parentPath = "";
            if (path.contains(config.getPathSeparatorSymbol())) {
                parentPath = path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
                parentPath += config.getPathSeparatorSymbol();
            }
            File targetFile = new File(parentPath + newFileName);
            
            if (!sourceFile.exists()) {
                throw new StorageMethodException("Source file does not exist: %s".formatted(path));
            }
            
            if (targetFile.exists()) {
                throw new StorageMethodException("Target file already exists: %s".formatted(newFileName));
            }
            
            // Create parent directories if they don't exist
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw new StorageMethodException("Failed to create parent directory for: %s".formatted(newFileName));
                }
            }
            
            if (!sourceFile.renameTo(targetFile)) {
                // Try using Files.move as fallback
                try {
                    Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new StorageMethodException("Failed to rename file from \"%s\" to \"%s\"".formatted(path, newFileName), e);
                }
            }
        } catch (Exception e) {
            throw new StorageMethodException("Failed to rename file \"%s\" to \"%s\" using local storage".formatted(path, newFileName), e);
        }
    }
}
