package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.FtpConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FtpStorage implements Storage {

    @Setter
    private String id = null;
    private final FtpConfig config;
    private final BackupManager backupManager;

    private FTPClient ftpClient = null;

    public FtpStorage(FtpConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
    }

    public FTPClient getClient() throws StorageConnectionException {
        if (ftpClient != null && ftpClient.isConnected() && ftpClient.isAvailable()) {
            try {
                if (ftpClient.sendNoOp()) {
                    return ftpClient;
                }
            } catch (IOException ignored) {
                // We shouldn't handle it, we'll just try to establish a new connection
            }
        }

        FTPClient ftp = new FTPClient();

        // Enable FTP logging
        // ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));

        ftp.setConnectTimeout(10 * 1000);
        ftp.setDefaultTimeout(30 * 1000);
        ftp.setDataTimeout(30 * 1000);
        ftp.setControlKeepAliveTimeout(30 * 1000);
        ftp.setControlEncoding("UTF-8");

        try {
            ftp.connect(config.getAddress(), config.getPort());
        } catch (IOException e) {
            throw new StorageConnectionException("Failed to establish FTP(S) connection", e);
        }
        int reply = ftp.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            try {
                ftp.disconnect();
            } catch (IOException ignored) {
                // It only can't disconnect if it's already disconnected
            }
            throw new StorageConnectionException("Failed to establish FTP(S) connection");
        }

        ftp.enterLocalPassiveMode();
        try {
            ftp.login(config.getUsername(), config.getPassword());
        } catch (IOException e) {
            throw new StorageConnectionException("Failed to login FTP(S) connection", e);
        }

        reply = ftp.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            try {
                ftp.disconnect();
            } catch (IOException ignored) {
                // It only can't disconnect if it's already disconnected
            }
            throw new StorageConnectionException("Failed to establish FTP(S) connection");
        }

        try {
            ftp.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
            ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftp.setListHiddenFiles(true);
        } catch (IOException e) {
            throw new StorageConnectionException("Failed to set FTP(S) connection parameters", e);
        }

        return ftp;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public Backup.StorageType getType() {
        return Backup.StorageType.FTP;
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
            if (!config.isEnabled()) {
                Backuper.getInstance().getLogManager().warn("FTP(S) server is disabled in config.yml", sender);
                return false;
            }

            getClient();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the FTP(S) server", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();

        try {
            ftp.changeWorkingDirectory(path);
            FTPFile[] files = ftp.listFiles();
            ftp.changeWorkingDirectory("");
            return Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get file list from dir \"%s\" using FTP(S) connection".formatted(path));
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
        String directory = path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
        String fileName = path.substring(path.lastIndexOf(config.getPathSeparatorSymbol()) + 1);

        FTPClient ftp = getClient();
        try {
            FTPFile[] files = ftp.listFiles(directory);
            if (files == null) {
                return false;
            }

            for (FTPFile file : files) {
                if (file.isFile() && file.getName().equals(fileName)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();
        try {
            ftp.changeWorkingDirectory("");
            return ftp.mlistFile(path).isFile();
        } catch (IOException e) {
            throw new StorageMethodException("Failed to check if \"%s\" in FTP(S) storage is a file or dir".formatted(path));
        }
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
        FTPClient ftp = getClient();

        try {
            long dirSize = 0;
            ftp.changeWorkingDirectory("");
            FTPFile currentDir = ftp.mlistFile(remoteFilePath);

            if (currentDir.isFile()) {
                dirSize += currentDir.getSize();
            }
            if (currentDir.isDirectory()) {

                if (!ftp.changeWorkingDirectory(remoteFilePath)) {
                    throw new StorageMethodException("Failed to change Working directory to \"%s\" using FTP(S) connection".formatted(remoteFilePath));
                }
                FTPFile[] files = ftp.listFiles();

                for (FTPFile file : files) {
                    if (file.getName().equals(".") || file.getName().equals("..")) {
                        continue;
                    }
                    dirSize += getDirByteSize(resolve(remoteFilePath, file.getName()));
                }
            }
            ftp.changeWorkingDirectory("");
            return dirSize;
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get \"%s\" dir size using FTP(S) connection".formatted(remoteFilePath), e);
        }
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();
        try {
            ftp.mkd(resolve(parentDir, newDirName));
        } catch (IOException e) {
            throw new StorageMethodException("Failed to create dir \"%s\" using FTP(S) connection".formatted(parentDir));
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String remoteParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();

        String remotePath = resolve(remoteParentDir, newFileName);
        try {
            ftp.setCopyStreamListener(new FtpStorageProgressListener(progressListener));
            if (!ftp.storeFile(remotePath, sourceStream)) {
                throw new StorageMethodException("Failed to send stream to \"%s\"".formatted(remotePath));
            }

        } catch (IOException e) {
            throw new StorageMethodException("Failed to upload stream to \"%s\"".formatted(remotePath));
        } finally {
            ftp.setCopyStreamListener(null);
        }
    }

    @Override
    public InputStream downloadFile(String remotePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();

        try {
            ftp.setCopyStreamListener(new FtpStorageProgressListener(progressListener));
            return ftp.retrieveFileStream(remotePath);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to get \"%s\" file's input stream from \"%s\" FTP(S) storage".formatted(remotePath, this.getId()));
        } finally {
            ftp.setCopyStreamListener(null);
        }
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();

        try {
            if (isFile(path)) {
                ftp.deleteFile(path);
            } else {
                ftp.removeDirectory(path);
            }
        } catch (IOException e) {
            throw new StorageMethodException("Failed to delete file/dir \"%s\" from FTP(S) storage".formatted(path));
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = getClient();
        try {
            String parentPath = "";
            if (path.contains(config.getPathSeparatorSymbol())) {
                parentPath = path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
                parentPath += config.getPathSeparatorSymbol();
            }
            ftp.rename(path, parentPath + newFileName);
        } catch (IOException e) {
            throw new StorageMethodException("Failed to rename file \"%s\" to \"%s\" using FTP(S) connection".formatted(path, newFileName));
        }
    }

    @Override
    public int getTransferSpeedMultiplier() {
        return 8;
    }

    public void disconnect() {
        try {
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.disconnect();
            }
        } catch (Exception ignored) {
            // Ignore disconnect errors
        }
        ftpClient = null;
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
}
