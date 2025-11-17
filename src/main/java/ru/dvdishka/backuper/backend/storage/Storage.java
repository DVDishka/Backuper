package ru.dvdishka.backuper.backend.storage;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.StorageConfig;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.util.BasicStorageProgressListener;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;

import java.io.InputStream;
import java.util.List;

public interface Storage {

    void setId(String id);

    String getId();

    StorageType getType();

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

    default void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        uploadFile(sourceStream, newFileName, targetParentDir, new BasicStorageProgressListener());
    }

    InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException;

    default InputStream downloadFile(String sourcePath) throws StorageMethodException, StorageConnectionException {
        return downloadFile(sourcePath, new BasicStorageProgressListener());
    }

    void downloadCompleted() throws StorageMethodException, StorageConnectionException;

    void delete(String path) throws StorageMethodException, StorageConnectionException;

    void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException;

    int getStorageSpeedMultiplier();

    default int getDeleteProgressMultiplier() {
        return getStorageSpeedMultiplier();
    }

    default int getTransferProgressMultiplier() {
        return getStorageSpeedMultiplier() * 5;
    }

    default int getZipProgressMultiplier() {
        return getStorageSpeedMultiplier() * 10;
    }

    void destroy();

}
