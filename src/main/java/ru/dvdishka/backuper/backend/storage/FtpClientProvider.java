package ru.dvdishka.backuper.backend.storage;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.FtpConfig;

import java.io.IOException;
import java.time.Duration;

public class FtpClientProvider {

    private final FtpConfig config;

    private FTPClient ftpClient = null;

    FtpClientProvider(FtpConfig config) {
        this.config = config;
    }

    synchronized FTPClient getClient() throws Storage.StorageConnectionException {
        if (ftpClient != null) {
            synchronized (ftpClient) {
                update();
                return ftpClient;
            }
        }

        ftpClient = new FTPClient();
        // Enable FTP logging
        // ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
        connect();
        return ftpClient;
    }

    public void update() {
        synchronized (ftpClient) {
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
        }
    }

    private void connect() {
        ftpClient.setConnectTimeout(30 * 1000);
        ftpClient.setDefaultTimeout(90 * 1000);
        ftpClient.setDataTimeout(Duration.ofSeconds(90));
        ftpClient.setControlKeepAliveTimeout(Duration.ofMinutes(5));
        ftpClient.setControlEncoding("UTF-8");

        try {
            ftpClient.connect(config.getAddress(), config.getPort());
        } catch (IOException e) {
            throw new Storage.StorageConnectionException("Failed to establish FTP(S) connection", e);
        }

        int reply = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            try {
                ftpClient.disconnect();
            } catch (IOException ignored) {
                // It only can't disconnect if it's already disconnected
            }
            throw new Storage.StorageConnectionException("Failed to establish FTP(S) connection");
        }

        ftpClient.enterLocalPassiveMode();
        try {
            ftpClient.login(config.getUsername(), config.getPassword());
        } catch (IOException e) {
            throw new Storage.StorageConnectionException("Failed to login FTP(S) connection", e);
        }
        reply = ftpClient.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            try {
                ftpClient.disconnect();
            } catch (IOException ignored) {
                // It only can't disconnect if it's already disconnected
            }
            throw new Storage.StorageConnectionException("Failed to establish FTP(S) connection");
        }
        try {
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftpClient.setListHiddenFiles(true);
            ftpClient.changeWorkingDirectory("");
        } catch (IOException e) {
            throw new Storage.StorageConnectionException("Failed to set FTP(S) connection parameters", e);
        }

        try {
            ftpClient.sendNoOp();
        } catch (IOException e) {
            Backuper.getInstance().getLogManager().warn("Failed to send FTP(S) server ping");
        }
    }

    synchronized void disconnect() {
        try {
            if (ftpClient != null && ftpClient.isConnected()) {
                ftpClient.disconnect();
            }
        } catch (Exception ignored) {
            // Ignore disconnect errors
        }
        ftpClient = null;
    }
}
