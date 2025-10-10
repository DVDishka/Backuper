package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.FtpConfig;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.util.Retriable;
import ru.dvdishka.backuper.backend.storage.util.StorageProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

public class FtpStorage implements PathStorage {

    @Setter
    private String id = null;
    private final FtpConfig config;
    private final BackupManager backupManager;

    private final FtpClientProvider mainClient;
    private final FtpClientProvider downloadClient;
    private final FtpClientProvider uploadClient;

    private final Retriable.RetriableExceptionHandler retriableExceptionHandler = new Retriable.RetriableExceptionHandler() {

        @Override
        public void handleRegularException(Exception e) {
            // Handle read errors and connection timeouts
            if (e instanceof SocketTimeoutException ||
                e.getMessage() != null && e.getMessage().contains("Read timed out")) {
                Backuper.getInstance().getLogManager().devWarn("FTP read timeout");
            }
            // Handle connection reset
            else if (e instanceof SocketException &&
                     (e.getMessage() != null && (e.getMessage().contains("Connection reset") ||
                                               e.getMessage().contains("Connection closed") ||
                                               e.getMessage().contains("Broken pipe")))) {
                Backuper.getInstance().getLogManager().devWarn("FTP connection reset");
            }
            // Handle passive mode parsing errors
            else if (e instanceof IOException &&
                     e.getMessage() != null && e.getMessage().contains("Could not parse passive host information")) {
                Backuper.getInstance().getLogManager().devWarn("FTP passive mode error");
            }
        }

        @Override
        public RuntimeException handleFinalException(Exception e) {
            // Authorization and connection errors
            if (e instanceof IOException) {
                if (e.getMessage() != null) {
                    // Authorization errors (code 421 - service unavailable)
                    if (e.getMessage().contains("421") || e.getMessage().contains("Failed to establish connection")) {
                        return new StorageConnectionException(getStorage(), "Failed to establish connection to FTP server", e);
                    }
                    // Timeout errors
                    else if (e.getMessage().contains("timed out") || e.getMessage().contains("Read timed out")) {
                        return new StorageConnectionException(getStorage(), "Connection timed out", e);
                    }
                    // PASV mode errors
                    else if (e.getMessage().contains("Could not parse passive host information")) {
                        return new StorageMethodException(getStorage(), "Failed to establish passive connection", e);
                    }
                    // Quota and storage limit errors
                    else if (e.getMessage().contains("550") &&
                             (e.getMessage().contains("quota exceeded") || e.getMessage().contains("disk full"))) {
                        return new StorageLimitException(getStorage(), "FTP storage quota exceeded", e);
                    }
                    // File access errors
                    else if (e.getMessage().contains("550") || e.getMessage().contains("Permission denied")) {
                        return new StorageMethodException(getStorage(), "Access denied or file not found", e);
                    }
                }
            }

            // Stream errors (in is null)
            if (e.getMessage() != null && e.getMessage().contains("in is null")) {
                return new StorageMethodException(getStorage(), "Failed to get input stream", e);
            }

            // Other errors
            return new StorageMethodException(getStorage(), e.getMessage(), e);
        }

        public Storage getStorage() {
            return FtpStorage.this;
        }
    };

    public FtpStorage(FtpConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
        this.mainClient = new FtpClientProvider(this);
        this.downloadClient = new FtpClientProvider(this);
        this.uploadClient = new FtpClientProvider(this);
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public StorageType getType() {
        return StorageType.FTP;
    }

    @Override
    public FtpConfig getConfig() {
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
            mainClient.getClient();
            downloadClient.getClient();
            uploadClient.getClient();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the FTP(S) server", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<List<String>>) () -> {
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                ftp.changeWorkingDirectory(path);
                FTPFile[] files = ftp.listFiles();
                if (files == null) {
                    throw new IOException("Failed to list files in directory: " + path);
                }
                return Arrays.stream(files)
                    .map(FTPFile::getName)
                    .filter(fileName -> !fileName.equals(".") && !fileName.equals(".."))
                    .toList();
            }
        }).retry(retriableExceptionHandler);
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
        return ((Retriable<Boolean>) () -> {
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                FTPFile file = ftp.mlistFile(path);
                return file != null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<Boolean>) () -> {
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                FTPFile file = ftp.mlistFile(path);
                return file != null && file.isFile();
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<Long>) () -> {
            FTPFile[] files = new FTPFile[0];
            long dirSize = 0;
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                FTPFile currentDir = ftp.mlistFile(path);

                if (currentDir == null) {
                    throw new IOException("File not found: " + path);
                }

                if (currentDir.isFile()) {
                    dirSize += currentDir.getSize();
                }
                else if (currentDir.isDirectory()) {
                    if (!ftp.changeWorkingDirectory(path)) {
                        throw new StorageMethodException(this, "Failed to change Working directory to \"%s\" using FTP(S) connection".formatted(path));
                    }
                    files = ftp.listFiles();
                    if (files == null) {
                        throw new StorageMethodException(this, "Failed to list files in directory: " + path);
                    }
                }
            }
            for (FTPFile file : files) {
                if (file.getName().equals(".") || file.getName().equals("..")) {
                    continue;
                }
                dirSize += getDirByteSize(resolve(path, file.getName()));
            }
            return dirSize;
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                ftp.mkd(resolve(parentDir, newDirName));
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener)
            throws StorageLimitException, StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            synchronized (uploadClient) {
                FTPClient ftp = uploadClient.getClient();
                String remotePath = resolve(targetParentDir, newFileName);
                ftp.setCopyStreamListener(new FtpStorageProgressListener(progressListener));
                if (!ftp.storeFile(remotePath, sourceStream)) {
                    throw new StorageMethodException(this, "Failed to upload stream to \"%s\"".formatted(remotePath), new RuntimeException(ftp.getReplyString()));
                }
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<InputStream>) () -> {
            synchronized (downloadClient) {
                FTPClient ftp = downloadClient.getClient();
                return new FtpStorageInputStream(ftp.retrieveFileStream(sourcePath), progressListener);
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void downloadCompleted() throws StorageMethodException, StorageConnectionException {
        try {
            synchronized (downloadClient) {
                FTPClient ftp = downloadClient.getClient();
                boolean completed = ftp.completePendingCommand();
                if (!completed) {
                    Backuper.getInstance().getLogManager().devWarn("FTP command completion returned false");
                }
            }
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().devWarn(e);
        }
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                boolean isFilePath = isFile(path);

                if (isFilePath) {
                    ftp.deleteFile(path);
                } else {
                    ftp.removeDirectory(path);
                }
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            synchronized (mainClient) {
                FTPClient ftp = mainClient.getClient();
                String parentPath = "";
                if (path.contains(config.getPathSeparatorSymbol())) {
                    parentPath = path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
                    parentPath += config.getPathSeparatorSymbol();
                }
                ftp.rename(path, parentPath + newFileName);
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public int getStorageSpeedMultiplier() {
        return 8;
    }

    @Override
    public void destroy() {
        mainClient.disconnect();
        downloadClient.disconnect();
        uploadClient.disconnect();
    }

    private static class FtpStorageProgressListener implements CopyStreamListener {

        private final StorageProgressListener progressListener;

        FtpStorageProgressListener(StorageProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void bytesTransferred(CopyStreamEvent copyStreamEvent) {
            progressListener.incrementProgress(copyStreamEvent.getBytesTransferred());
        }

        @Override
        public void bytesTransferred(long totalBytesTransferred, int delta, long totalStreamSize) {
            progressListener.incrementProgress(delta);
        }
    }

    private static class FtpStorageInputStream extends InputStream {

        private final InputStream inputStream;
        private final StorageProgressListener progressListener;

        FtpStorageInputStream(InputStream inputStream, StorageProgressListener progressListener) {
            this.inputStream = inputStream;
            this.progressListener = progressListener;
        }

        @Override
        public int read() throws IOException {
            int result = inputStream.read();
            if (result != -1) {
                progressListener.incrementProgress(1);
            }
            return result;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int bytesRead = inputStream.read(b);
            if (bytesRead > 0) {
                progressListener.incrementProgress(bytesRead);
            }
            return bytesRead;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int bytesRead = inputStream.read(b, off, len);
            if (bytesRead > 0) {
                progressListener.incrementProgress(bytesRead);
            }
            return bytesRead;
        }
    }}
