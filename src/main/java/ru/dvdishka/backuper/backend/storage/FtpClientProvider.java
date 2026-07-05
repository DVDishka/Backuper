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
    private final String clientRole;

    private FTPClient ftpClient = null; // Effectively final
    private String defaultPath = ".";

    FtpClientProvider(FtpStorage storage, String clientRole) {
        this.storage = storage;
        this.clientRole = clientRole;
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
            resetWorkingDirectory();
            return ftpClient;
        }

        ftpClient = new FTPClient();
        addProtocolLogger();
        connect();
        try {
            defaultPath = ftpClient.printWorkingDirectory();
        } catch (IOException e) {
            Backuper.getInstance().getLogManager().warn("Failed to get default %s working directory".formatted(storage.getId()));
        }
        return ftpClient;
    }

    synchronized void resetWorkingDirectory() {
        try {
            if (!ftpClient.changeWorkingDirectory(defaultPath)) {
                Backuper.getInstance().getLogManager().devWarn("Failed to reset FTP working directory to \"%s\". FTP reply: %s".formatted(defaultPath, ftpClient.getReplyString()));
            }
        } catch (Exception ignored) {
        }
    }

    private void connect() {
        if (ftpClient == null) {
            ftpClient = new FTPClient();
            addProtocolLogger();
        }
        logLifecycle("Connecting to %s:%d".formatted(storage.getConfig().getAddress(), storage.getConfig().getPort()));
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
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftpClient.setListHiddenFiles(true);
        } catch (IOException e) {
            throw new StorageConnectionException(storage, "Failed to set FTP(S) connection parameters", e);
        }

        try {
            ftpClient.sendNoOp();
        } catch (IOException e) {
            Backuper.getInstance().getLogManager().warn("Failed to send FTP(S) server ping");
        }
        logLifecycle("Connected. Default path: %s".formatted(getCurrentWorkingDirectory()));
    }

    void disconnect() {
        if (ftpClient != null) {
            try {
                logLifecycle("Disconnecting");
                ftpClient.disconnect();
            } catch (Exception ignored) {
                // Ignore disconnect errors
            }
        }
        ftpClient = null;
    }

    private void addProtocolLogger() {
        FtpProtocolLogger protocolLogger = storage.getProtocolLogger();
        if (protocolLogger != null) {
            ftpClient.addProtocolCommandListener(protocolLogger.createListener(clientRole));
        }
    }

    private void logLifecycle(String message) {
        FtpProtocolLogger protocolLogger = storage.getProtocolLogger();
        if (protocolLogger != null) {
            protocolLogger.logLifecycle(clientRole, message);
        }
    }

    private String getCurrentWorkingDirectory() {
        try {
            return ftpClient.printWorkingDirectory();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
