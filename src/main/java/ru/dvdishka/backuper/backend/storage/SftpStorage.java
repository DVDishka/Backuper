package ru.dvdishka.backuper.backend.storage;

import com.jcraft.jsch.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.BackupManager;
import ru.dvdishka.backuper.backend.config.SftpConfig;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

public class SftpStorage implements PathStorage {

    private String id = null;
    private final SftpConfig config;
    private final BackupManager backupManager;

    private Session sshSession = null;
    private ChannelSftp sftpChannel = null;

    public SftpStorage(SftpConfig config) {
        this.config = config;
        this.backupManager = new BackupManager(this);
    }

    private Pair<Session, ChannelSftp> getClient() throws StorageConnectionException {
        if (sshSession != null && sshSession.isConnected() && sftpChannel != null && sftpChannel.isConnected()) {
            try {
                // Test if the connection is still alive
                sftpChannel.pwd();
                return Pair.of(sshSession, sftpChannel);
            } catch (SftpException ignored) {
                // Connection is dead, we'll create a new one
            }
        }

        if (!config.getAuthType().equals("password") && !config.getAuthType().equals("key") && !config.getAuthType().equals("key_pass")) {
            throw new StorageConnectionException("Wrong auth type \"%s\"".formatted(config.getAuthType()));
        }

        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channel = null;

        try {
            if (!config.getSshConfigFile().isEmpty()) {
                jsch.setConfigRepository(OpenSSHConfig.parseFile(config.getSshConfigFile()));
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
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(15000);

                // Store the connection for reuse
                this.sshSession = session; // Already not null
                this.sftpChannel = channel; // Already not null

                return Pair.of(session, channel);
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
            throw new StorageConnectionException("Failed to establish SFTP connection", e);
        }

        throw new StorageConnectionException("Failed to establish SFTP connection");
    }

    public void setId(String id) {
        this.id = id;
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

            getClient();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the SFTP server", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    @Override
    public List<String> ls(String path) throws StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            Vector<ChannelSftp.LsEntry> ls = sftp.ls(path);
            ArrayList<String> files = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : ls) {
                files.add(entry.getFilename());
            }
            return files;
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to get file list from dir \"%s\" using SFTP connection".formatted(path), e);
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
        try {
            getClient().getRight().stat(path);
            return true;
        } catch (SftpException e) {
            return false;
        }
    }

    @Override
    public boolean isFile(String path) throws StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            return !sftp.stat(path).isDir();
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to check if \"%s\" is a file or dir".formatted(path));
        }
    }

    @Override
    public long getDirByteSize(String path) throws StorageMethodException, StorageConnectionException {
        try {
            Pair<Session, ChannelSftp> client = getClient();
            ChannelSftp sftpChannel = client.getRight();
            
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
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to get \"%s\" dir size using SFTP connection".formatted(path), e);
        }
    }

    @Override
    public void createDir(String newDirName, String parentDir) throws StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            sftp.mkdir(resolve(parentDir, newDirName));
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to create dir \"%s\" using SFTP connection".formatted(parentDir), e);
        }
    }

    @Override
    public void uploadFile(InputStream sourceStream, String newFileName, String targetParentDir, StorageProgressListener progressListener) throws StorageLimitException, StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            String remotePath = resolve(targetParentDir, newFileName);
            sftp.put(sourceStream, remotePath, new SftpStorageProgressListener(progressListener));
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to upload stream to SFTP server");
        }
    }

    @Override
    public InputStream downloadFile(String sourcePath, StorageProgressListener progressListener) throws StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            return sftp.get(sourcePath, new SftpStorageProgressListener(progressListener));
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to download file \"%s\" from SFTP server", e);
        }
    }

    @Override
    public void delete(String path) throws StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            SftpATTRS stat = sftp.stat(path);
            if (stat.isDir()) {
                sftp.rmdir(path);
            } else {
                long fileSize = stat.getSize();
                sftp.rm(path);
            }
        } catch (SftpException e) {
            throw new StorageMethodException("Failed to delete file/dir \"%s\" from SFTP server");
        }
    }

    @Override
    public void renameFile(String path, String newFileName) throws StorageMethodException, StorageConnectionException {
        Pair<Session, ChannelSftp> client = getClient();
        ChannelSftp sftp = client.getRight();

        try {
            String parentPath = "";
            if (path.contains(config.getPathSeparatorSymbol())) {
                parentPath = path.substring(0, path.lastIndexOf(config.getPathSeparatorSymbol()));
                parentPath += config.getPathSeparatorSymbol();
            }
            sftp.rename(path, parentPath + newFileName);

        } catch (SftpException e) {
            throw new StorageMethodException("Failed to rename file \"%s\" to \"%s\" using SFTP connection".formatted(path, newFileName), e);
        }
    }

    @Override
    public int getTransferSpeedMultiplier() {
        return 8;
    }

    public void disconnect() {
        try {
            if (sftpChannel != null && sftpChannel.isConnected()) {
                sftpChannel.exit();
            }
        } catch (Exception ignored) {
            // Ignore disconnect errors
        }
        try {
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
        } catch (Exception ignored) {
            // Ignore disconnect errors
        }
        sftpChannel = null;
        sshSession = null;
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
