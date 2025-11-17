package ru.dvdishka.backuper.backend.storage;

import com.jcraft.jsch.*;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;

import java.util.Properties;

public class SftpClientProvider {

    private final SftpStorage storage;

    private Session sshSession = null; // Effectively final
    private ChannelSftp sftpChannel = null; // Effectively final

    SftpClientProvider(SftpStorage storage) {
        this.storage = storage;
    }

    synchronized ChannelSftp getClient() {
        if (sshSession != null && sftpChannel != null) {
            try {
                // Test if the connection is still alive
                if (sftpChannel.isConnected() && sftpChannel.pwd() != null) return sftpChannel;
                sftpChannel.connect();
            } catch (Exception ignored) {
                connect();
                try {
                    if (sftpChannel.isConnected() && sftpChannel.pwd() != null) return sftpChannel;
                    throw new StorageConnectionException(storage, "Failed to connect to establish sftp connection");
                } catch (SftpException e) {
                    throw new StorageConnectionException(storage, "Failed to connect to establish sftp connection", e);
                }
            }
        }
        connect();
        try {
            if (sftpChannel.isConnected() && sftpChannel.pwd() != null) return sftpChannel;
            throw new StorageConnectionException(storage, "Failed to connect to establish sftp connection");
        } catch (SftpException e) {
            throw new StorageConnectionException(storage, "Failed to connect to establish sftp connection", e);
        }
    }

    private void connect() {

        if (!storage.getConfig().getAuthType().equals("password") && !storage.getConfig().getAuthType().equals("key") && !storage.getConfig().getAuthType().equals("key_pass")) {
            throw new StorageConnectionException(storage, "Wrong auth type \"%s\"".formatted(storage.getConfig().getAuthType()));
        }

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;

        try {
            if (!storage.getConfig().getSshConfigFilePath().isEmpty()) {
                jsch.setConfigRepository(OpenSSHConfig.parseFile(storage.getConfig().getSshConfigFilePath()));
            } else {
                if (storage.getConfig().getAuthType().equals("key")) {
                    jsch.addIdentity(storage.getConfig().getKeyFilePath());
                }
                if (storage.getConfig().getAuthType().equals("key_pass")) {
                    jsch.addIdentity(storage.getConfig().getKeyFilePath(), storage.getConfig().getPassword());
                }

                session = jsch.getSession(storage.getConfig().getUsername(), storage.getConfig().getAddress(), storage.getConfig().getPort());

                if (storage.getConfig().getAuthType().equals("password")) {
                    session.setPassword(storage.getConfig().getPassword());
                }

                Properties properties = new Properties();
                if (this.storage.getConfig().getUseKnownHostsFile().equals("false")) {
                    properties.put("StrictHostKeyChecking", "no");
                } else {
                    properties.put("StrictHostKeyChecking", "yes");
                }
                session.setConfig(properties);

                if (!this.storage.getConfig().getUseKnownHostsFile().equals("false")) {
                    jsch.setKnownHosts(this.storage.getConfig().getKnownHostsFilePath());
                }

                session.connect(15000);
                session.setServerAliveInterval(60 * 1000);
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(15000);

                this.sshSession = session; // Already not null
                this.sftpChannel = channel; // Already not null
            }
        } catch (Exception e) {
            try {
                if (channel != null) {
                    channel.exit();
                }
            } catch (Exception ignored) {
                // An error only occurs if the channel is null, so we don't need to handle it
            }
            try {
                if (session != null) {
                    session.disconnect();
                }
            } catch (Exception ignored) {
                // An error only occurs if the ssh session is null, so we don't need to handle it
            }
            throw new StorageConnectionException(storage, "Failed to establish SFTP connection", e);
        }
    }

    void disconnect() {
        if (sftpChannel != null) {
            try {
                sftpChannel.disconnect();
            } catch (Exception ignored) {
                // Ignore disconnect errors
            }
            try {
                sshSession.disconnect();
            } catch (Exception ignored) {
                // Ignored disconnect errors
            }
        }
        sftpChannel = null;
        sshSession = null;
    }
}
