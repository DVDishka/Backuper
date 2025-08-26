package ru.dvdishka.backuper.backend.storage;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.StorageConfig;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public interface Storage {

    void setId(String id);

    String getId();

    Backup.StorageType getType();

    StorageConfig getConfig();

    BackupManager getBackupManager();

    /***
     * Checks if the FTP(S) server is available. Sends warning to the console if not
     */
    boolean checkConnection();

    /***
     * Checks if the FTP(S) server is available. Sends warning to the console and sender if not
     */
    boolean checkConnection(CommandSender sender);

    List<String> ls(String path) throws StorageMethodException, StorageConnectionException;

    String resolve(String path, String fileName) throws StorageMethodException;

    boolean isFile(String path) throws StorageMethodException, StorageConnectionException;

    default boolean isDir(String path) throws StorageMethodException, StorageConnectionException {
        return !isFile(path);
    }

    long getDirByteSize(String remoteFilePath) throws StorageMethodException, StorageConnectionException;

    void createDir(String newDirName, String parentDir) throws StorageLimitException, StorageMethodException, StorageConnectionException;

    void uploadFile(File file, String newFileName, String remoteParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException;

    void uploadFile(InputStream sourceStream, String newFileName, String remoteParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException;

    void downloadFile(String remotePath, File targetFile, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException;

    void delete(String path) throws StorageMethodException, StorageConnectionException;

    void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException;

    class StorageConnectionException extends RuntimeException {
        public StorageConnectionException(String message) {
            super(message);
        }

        public StorageConnectionException(String message, Exception e) {
            super(message, e);
        }
    }

    class StorageMethodException extends RuntimeException {
        public StorageMethodException(String message) {
            super(message);
        }

        public StorageMethodException(String message, Exception e) {
            super(message, e);
        }
    }

    class StorageLimitException extends RuntimeException {
        private final Backup.StorageType storageType;

        public StorageLimitException(Backup.StorageType storageType) {
            super("%s storage space limit reached".formatted(storageType.name()));
            this.storageType = storageType;
        }

        public Backup.StorageType getStorageType() {
            return storageType;
        }}

    class StorageQuotaExceededException extends RuntimeException {

        private final Backup.StorageType storageType;

        public StorageQuotaExceededException(Backup.StorageType storageType) {
            super("%s storage quota limit reached, try again later".formatted(storageType.name()));
            this.storageType = storageType;
        }

        public Backup.StorageType getStorageType() {
            return storageType;
        }
    }
}
