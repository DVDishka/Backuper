package ru.dvdishka.backuper.backend.storage;

import lombok.Setter;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.io.CopyStreamEvent;
import org.apache.commons.net.io.CopyStreamListener;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.FtpConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class FtpStorage implements PathStorage {

    @Setter
    private String id = null;
    private final FtpConfig config;
    private final BackupManager backupManager;

    private final FtpClient mainClient;
    private final FtpClient downloadClient;
    private final FtpClient uploadClient;

    public FtpStorage(FtpConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
        this.mainClient = new FtpClient(config);
        this.downloadClient = new FtpClient(config);
        this.uploadClient = new FtpClient(config);
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
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
            try {
                ftp.changeWorkingDirectory(path);
                FTPFile[] files = ftp.listFiles();
                return Arrays.stream(files).map(FTPFile::getName).filter(fileName -> !fileName.equals(".") && !fileName.equals("..")).toList();
            } catch (IOException e) {
                throw new StorageMethodException("Failed to get file list from dir \"%s\" using FTP(S) connection".formatted(path), e);
            }
        }
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
            try {
                FTPFile file = ftp.mlistFile(path);
                return file != null;
            } catch (Exception e) {
                throw new StorageMethodException("Failed to check if file \"%s\" exists using FTP(S) connection".formatted(path), e);
            }
        }
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
            try {
                return ftp.mlistFile(path).isFile();
            } catch (IOException e) {
                throw new StorageMethodException("Failed to check if \"%s\" in FTP(S) storage is a file or dir".formatted(path), e);
            }
        }
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
            try {
                long dirSize = 0;
                FTPFile currentDir = ftp.mlistFile(path);

                if (currentDir.isFile()) {
                    dirSize += currentDir.getSize();
                }
                if (currentDir.isDirectory()) {

                    if (!ftp.changeWorkingDirectory(path)) {
                        throw new StorageMethodException("Failed to change Working directory to \"%s\" using FTP(S) connection".formatted(path));
                    }
                    FTPFile[] files = ftp.listFiles();

                    for (FTPFile file : files) {
                        if (file.getName().equals(".") || file.getName().equals("..")) {
                            continue;
                        }
                        dirSize += getDirByteSize(resolve(path, file.getName()));
                    }
                }
                return dirSize;
            } catch (Exception e) {
                throw new StorageMethodException("Failed to get \"%s\" dir size using FTP(S) connection".formatted(path), e);
            }
        }
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
            try {
                ftp.mkd(resolve(parentDir, newDirName));
            } catch (IOException e) {
                throw new StorageMethodException("Failed to create dir \"%s\" using FTP(S) connection".formatted(parentDir), e);
            }
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        FTPClient ftp = uploadClient.getClient();
        synchronized (ftp) {
            String remotePath = resolve(targetParentDir, newFileName);
            try {
                ftp.setCopyStreamListener(new FtpStorageProgressListener(progressListener));
                if (!ftp.storeFile(remotePath, sourceStream)) {
                    throw new StorageMethodException("Failed to upload stream to \"%s\"".formatted(remotePath), new RuntimeException(ftp.getReplyString()));
                }

            } catch (IOException e) {
                throw new StorageMethodException("Failed to upload stream to \"%s\"".formatted(remotePath), e);
            }
        }
    }

    @Override
    public InputStream downloadFile(String sourcePath) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = downloadClient.getClient();
        synchronized (ftp) {
            try {
                return ftp.retrieveFileStream(sourcePath);
            } catch (IOException e) {
                throw new StorageMethodException("Failed to get \"%s\" file's input stream from \"%s\" FTP(S) storage".formatted(sourcePath, this.getId()), e);
            }
        }
    }

    @Override
    public void downloadCompleted() throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = downloadClient.getClient();
        synchronized (ftp) {
            try {
                ftp.completePendingCommand();
            } catch (IOException e) {
                throw new StorageMethodException("Failed to complete pending command", e);
            }
        }
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
            try {
                if (isFile(path)) {
                    ftp.deleteFile(path);
                } else {
                    ftp.removeDirectory(path);
                }
            } catch (IOException e) {
                throw new StorageMethodException("Failed to delete file/dir \"%s\" from FTP(S) storage".formatted(path), e);
            }
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        FTPClient ftp = mainClient.getClient();
        synchronized (ftp) {
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
    }

    @Override
    public int getTransferSpeedMultiplier() {
        return 8;
    }

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
}
