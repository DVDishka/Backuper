package ru.dvdishka.backuper.backend.storage;

import com.jcraft.jsch.*;
import ru.dvdishka.backuper.backend.config.SftpConfig;

import java.util.Properties;

public class SftpClientProvider {

    private final SftpConfig config;

    private Session sshSession = null; // Effectively final
    private ChannelSftp sftpChannel = null; // Effectively final

    SftpClientProvider(SftpConfig config) {
        this.config = config;
    }

    synchronized public ChannelSftp getChannel() {
        if (sshSession != null && sftpChannel != null) {
            try {
                // Test if the connection is still alive
                sftpChannel.pwd();
                return sftpChannel;
            } catch (SftpException ignored) {
                connect();
                try {
                    sftpChannel.pwd();
                    return sftpChannel;
                } catch (SftpException e) {
                    throw new Storage.StorageConnectionException("Failed to connect to establish sftp connection", e);
                }
            }
        }
        connect();
        try {
            sftpChannel.pwd();
            return sftpChannel;
        } catch (SftpException e) {
            throw new Storage.StorageConnectionException("Failed to connect to establish sftp connection", e);
        }
    }

    private void connect() {

        if (!config.getAuthType().equals("password") && !config.getAuthType().equals("key") && !config.getAuthType().equals("key_pass")) {
            throw new Storage.StorageConnectionException("Wrong auth type \"%s\"".formatted(config.getAuthType()));
        }

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;

        try {
            if (!config.getSshConfigFilePath().isEmpty()) {
                jsch.setConfigRepository(OpenSSHConfig.parseFile(config.getSshConfigFilePath()));
            } else {
                if (config.getAuthType().equals("key")) {
                    jsch.addIdentity(config.getKeyFilePath());
                }
                if (config.getAuthType().equals("key_pass")) {
                    jsch.addIdentity(config.getKeyFilePath(), config.getPassword());
                }

                session = jsch.getSession(config.getUsername(), config.getAddress(), config.getPort());

                if (config.getAuthType().equals("password")) {
                    session.setPassword(config.getPassword());
                }

                Properties config = new Properties();
                if (this.config.getUseKnownHostsFile().equals("false")) {
                    config.put("StrictHostKeyChecking", "no");
                } else {
                    config.put("StrictHostKeyChecking", "yes");
                }
                session.setConfig(config);

                if (!this.config.getUseKnownHostsFile().equals("false")) {
                    jsch.setKnownHosts(this.config.getKnownHostsFilePath());
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
            throw new Storage.StorageConnectionException("Failed to establish SFTP connection", e);
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
