package ru.dvdishka.backuper.backend.storage;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.StorageConfig;

import java.io.InputStream;
import java.util.List;

public interface Storage {

    void setId(String id);

    String getId();

    Backup.StorageType getType();

    StorageConfig getConfig();

    BackupManager getBackupManager();

    /***
     * Checks if storage is available. Sends warning to the console if not
     */
    boolean checkConnection();

    /***
     * Checks if storage is available. Sends warning to the console and sender if not
     */
    boolean checkConnection(CommandSender sender);

    /***
     * @return Returns a list of file names
     */
    List<String> ls(String path) throws StorageMethodException, StorageConnectionException;

    String resolve(String path, String fileName) throws StorageMethodException;

    boolean exists(String path) throws StorageMethodException, StorageConnectionException;

    boolean isFile(String path) throws StorageMethodException, StorageConnectionException;

    default boolean isDir(String path) throws StorageMethodException, StorageConnectionException {
        return !isFile(path);
    }

    String getFileNameFromPath(String path) throws StorageMethodException, StorageConnectionException;

    String getParentPath(String path) throws StorageMethodException, StorageConnectionException;

    long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException;

    void createDir(String newDirName, String parentDir) throws StorageLimitException, StorageMethodException, StorageConnectionException;

    void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException;

    InputStream downloadFile(String sourcePath) throws StorageMethodException, StorageConnectionException;

    void downloadCompleted() throws StorageMethodException, StorageConnectionException;

    void delete(String path) throws StorageMethodException, StorageConnectionException;

    void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException;

    int getTransferSpeedMultiplier();

    void destroy();

    class StorageConnectionException extends RuntimeException {
        public StorageConnectionException(String message) {
            super(message);
        }

        public StorageConnectionException(String message, Exception e) {
            super("%s\n%s".formatted(message, e.getMessage()), e);
            this.setStackTrace(e.getStackTrace());
        }
    }

    class StorageMethodException extends RuntimeException {
        public StorageMethodException(String message) {
            super(message);
        }

        public StorageMethodException(String message, Exception e) {
            super("%s\n%s".formatted(message, e.getMessage()), e);
            this.setStackTrace(e.getStackTrace());
        }
    }

    class StorageLimitException extends RuntimeException {
        public StorageLimitException(String message) {
            super(message);
        }

        public StorageLimitException(String message, Exception e) {
            super("%s\n%s".formatted(message, e.getMessage()), e);
            this.setStackTrace(e.getStackTrace());
        }
    }

    class StorageQuotaExceededException extends RuntimeException {

        public StorageQuotaExceededException(String message) {
            super(message);
        }

        public StorageQuotaExceededException(String message, Exception e) {
            super("%s\n%s".formatted(message, e.getMessage()), e);
            this.setStackTrace(e.getStackTrace());
        }    }
}
