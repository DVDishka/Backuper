package ru.dvdishka.backuper.backend.storage;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;

import java.io.IOException;
import java.time.Duration;

public class FtpClientProvider {

    private final FtpStorage storage;

    private FTPClient ftpClient = null; // Effectively final

    FtpClientProvider(FtpStorage storage) {
        this.storage = storage;
    }

    synchronized FTPClient getClient() throws StorageConnectionException {
        if (ftpClient != null) {
            try {
                if (!ftpClient.isConnected() || !ftpClient.isAvailable()) {
                    connect();
                }
                try {
                    if (!ftpClient.sendNoOp()) {
                        connect();
                        ftpClient.sendNoOp();
                    }
                } catch (Exception e) {
                    connect();
                    ftpClient.sendNoOp();
                }
            } catch (IOException e) {
                Backuper.getInstance().getLogManager().warn("Failed to reconnect to FTP(S) connection");
                Backuper.getInstance().getLogManager().warn(e);
            }
            try {
                ftpClient.changeWorkingDirectory("");
            } catch (Exception ignored) {
            }
            return ftpClient;
        }

        ftpClient = new FTPClient();
        // Enable FTP logging
        // ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
        connect();
        return ftpClient;
    }

    private void connect() {
        ftpClient.setConnectTimeout(30 * 1000);
        ftpClient.setDefaultTimeout(30 * 1000);
        ftpClient.setDataTimeout(Duration.ofSeconds(30));
        ftpClient.setControlKeepAliveTimeout(Duration.ofMinutes(5));
        ftpClient.setControlEncoding("UTF-8");

        try {
            ftpClient.connect(storage.getConfig().getAddress(), storage.getConfig().getPort());
        } catch (IOException e) {
            throw new StorageConnectionException(storage, "Failed to establish FTP(S) connection", e);
        }

        int reply = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            try {
                ftpClient.disconnect();
            } catch (IOException ignored) {
                // It only can't disconnect if it's already disconnected
            }
            throw new StorageConnectionException(storage, "Failed to establish FTP(S) connection");
        }

        ftpClient.enterLocalPassiveMode();
        try {
            ftpClient.login(storage.getConfig().getUsername(), storage.getConfig().getPassword());
        } catch (IOException e) {
            throw new StorageConnectionException(storage, "Failed to login FTP(S) connection", e);
        }
        reply = ftpClient.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            try {
                ftpClient.disconnect();
            } catch (IOException ignored) {
                // It only can't disconnect if it's already disconnected
            }
            throw new StorageConnectionException(storage, "Failed to establish FTP(S) connection");
        }
        try {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftpClient.setListHiddenFiles(true);
            ftpClient.changeWorkingDirectory("");
        } catch (IOException e) {
            throw new StorageConnectionException(storage, "Failed to set FTP(S) connection parameters", e);
        }

        try {
            ftpClient.sendNoOp();
        } catch (IOException e) {
            Backuper.getInstance().getLogManager().warn("Failed to send FTP(S) server ping");
        }
    }

    void disconnect() {
        if (ftpClient != null) {
            try {
                ftpClient.disconnect();
            } catch (Exception ignored) {
                // Ignore disconnect errors
            }
        }
        ftpClient = null;
    }
}
