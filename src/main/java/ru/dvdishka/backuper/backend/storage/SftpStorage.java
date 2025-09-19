package ru.dvdishka.backuper.backend.storage;

import com.jcraft.jsch.*;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.SftpConfig;
import ru.dvdishka.backuper.backend.storage.util.Retriable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class SftpStorage implements PathStorage {

    @Setter
    private String id = null;
    private final SftpConfig config;
    private final BackupManager backupManager;

    private final SftpClientProvider mainClient;
    private final SftpClientProvider downloadClient;
    private final SftpClientProvider uploadClient;

    private static final Retriable.RetriableExceptionHandler retriableExceptionHandler = new Retriable.RetriableExceptionHandler() {

        @Override
        public void handleRegularException(Exception e) {
            // Handle connection timeouts
            if (e instanceof java.net.SocketTimeoutException ||
                (e.getMessage() != null && e.getMessage().contains("Read timed out"))) {
                Backuper.getInstance().getLogManager().devWarn("SFTP read timeout");
            }
            // Handle connection reset
            else if (e instanceof java.net.SocketException &&
                    (e.getMessage() != null && (e.getMessage().contains("Connection reset") ||
                                              e.getMessage().contains("Connection closed") ||
                                              e.getMessage().contains("Broken pipe")))) {
                Backuper.getInstance().getLogManager().devWarn("SFTP connection reset");
            }
            // Handle SSH errors
            else if (e instanceof JSchException &&
                    e.getMessage() != null && e.getMessage().contains("session is down")) {
                Backuper.getInstance().getLogManager().devWarn("SFTP session is down");
            }
        }

        @Override
        public RuntimeException handleFinalException(Exception e) {
            // Authentication and connection errors
            if (e instanceof JSchException) {
                if (e.getMessage() != null) {
                    // Authentication errors
                    if (e.getMessage().contains("auth fail") ||
                        e.getMessage().contains("Authentication fail")) {
                        return new StorageConnectionException("Authentication failed to SFTP server", e);
                    }
                    // Host connection errors
                    else if (e.getMessage().contains("UnknownHostException") ||
                             e.getMessage().contains("Connection refused") ||
                             e.getMessage().contains("connect failed")) {
                        return new StorageConnectionException("Failed to establish connection to SFTP server", e);
                    }
                    // Timeout errors
                    else if (e.getMessage().contains("timeout") ||
                             e.getMessage().contains("timed out") ||
                             e.getMessage().contains("session is down")) {
                        return new StorageConnectionException("Connection timed out", e);
                    }
                }
            }

            // File operation errors
            if (e instanceof SftpException) {
                SftpException sftpException = (SftpException) e;
                // "No such file" error
                if (sftpException.id == 2) {
                    return new StorageMethodException("File not found", e);
                }
                // "Permission denied" error
                else if (sftpException.id == 3 || sftpException.id == 4) {
                    return new StorageMethodException("Permission denied", e);
                }
                // "Disk full" error
                else if (sftpException.id == 5 ||
                        (e.getMessage() != null && (e.getMessage().contains("disk full") ||
                                                  e.getMessage().contains("quota exceeded")))) {
                    return new StorageLimitException("SFTP storage quota exceeded", e);
                }
            }

            // Other errors
            return new StorageMethodException(e.getMessage(), e);
        }
    };

    public SftpStorage(SftpConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
        this.mainClient = new SftpClientProvider(config);
        this.downloadClient = new SftpClientProvider(config);
        this.uploadClient = new SftpClientProvider(config);
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public Backup.StorageType getType() {
        return Backup.StorageType.SFTP;
    }

    @Override
    public SftpConfig getConfig() {
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
                Backuper.getInstance().getLogManager().warn("SFTP server is disabled in config.yml", sender);
                return false;
            }

            mainClient.getChannel();
            downloadClient.getChannel();
            uploadClient.getChannel();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the SFTP server", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<List<String>>) () -> {
            ChannelSftp sftp = mainClient.getChannel();
            synchronized (sftp) {
                Vector<ChannelSftp.LsEntry> ls = sftp.ls(path);
                ArrayList<String> files = new ArrayList<>();
                for (ChannelSftp.LsEntry entry : ls) {
                    files.add(entry.getFilename());
                }
                return files;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public boolean exists(String path) throws StorageMethodException, StorageConnectionException {
        ChannelSftp sftp = mainClient.getChannel();
        synchronized (sftp) {
            try {
                mainClient.getChannel().stat(path);
                return true;
            } catch (SftpException e) {
                return false;
            }
        }
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<Boolean>) () -> {
            ChannelSftp sftp = mainClient.getChannel();
            synchronized (sftp) {
                return !sftp.stat(path).isDir();
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<Long>) () -> {
            ChannelSftp sftpChannel = mainClient.getChannel();
            synchronized (sftpChannel) {
                long dirSize = 0;
                if (!sftpChannel.stat(path).isDir()) {
                    dirSize += sftpChannel.stat(path).getSize();
                } else {
                    for (ChannelSftp.LsEntry entry : sftpChannel.ls(path)) {
                        if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                            continue;
                        }
                        dirSize += getDirByteSize(resolve(path, entry.getFilename()));
                    }
                }
                return dirSize;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            ChannelSftp sftp = mainClient.getChannel();
            synchronized (sftp) {
                sftp.mkdir(resolve(parentDir, newDirName));
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            ChannelSftp sftp = uploadClient.getChannel();
            synchronized (sftp) {
                String remotePath = resolve(targetParentDir, newFileName);
                sftp.put(sourceStream, remotePath, new SftpStorageProgressListener(progressListener));
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public InputStream downloadFile(String sourcePath) throws StorageMethodException, StorageConnectionException {
        return ((Retriable<InputStream>) () -> {
            ChannelSftp sftp = downloadClient.getChannel();
            synchronized (sftp) {
                return sftp.get(sourcePath);
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            ChannelSftp sftp = mainClient.getChannel();
            synchronized (sftp) {
                SftpATTRS stat = sftp.stat(path);
                if (stat.isDir()) {
                    sftp.rmdir(path);
                } else {
                    long fileSize = stat.getSize();
                    sftp.rm(path);
                }
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        ((Retriable<Void>) () -> {
            ChannelSftp sftp = mainClient.getChannel();
            synchronized (sftp) {
                String parentPath = "";
                if (path.contains(config.getPathSeparatorSymbol())) {
                    parentPath = path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
                    parentPath += config.getPathSeparatorSymbol();
                }
                sftp.rename(path, parentPath + newFileName);
                return null;
            }
        }).retry(retriableExceptionHandler);
    }

    @Override
    public int getTransferSpeedMultiplier() {
        return 8;
    }

    @Override
    public void destroy() {
        mainClient.disconnect();
        downloadClient.disconnect();
        uploadClient.disconnect();
    }

    @Override
    public void downloadCompleted() throws StorageMethodException, StorageConnectionException {
        // No additional actions required for SFTP
    }

    private static class SftpStorageProgressListener implements SftpProgressMonitor {

        private final StorageProgressListener progressListener;

        SftpStorageProgressListener(StorageProgressListener progressListener) {
            this.progressListener = progressListener;
        }

        @Override
        public void init(int operationCode, String sourceDir, String destDir, long maxProgress) {
        }

        @Override
        public boolean count(long l) {
            progressListener.incrementProgress(l);
            return true;
        }

        @Override
        public void end() {

        }
    }
}
