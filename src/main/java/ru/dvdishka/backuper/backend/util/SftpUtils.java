package ru.dvdishka.backuper.backend.util;

import com.jcraft.jsch.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;

import javax.security.sasl.AuthenticationException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Vector;

public class SftpUtils {

    private static String sshConfigFile;
    private static String authType;
    private static String address;
    private static String username;
    private static String password;
    private static String backupsFolder;
    private static String keyFilePath;
    private static String useKnownHostsFile;
    private static String knownHostsFilePath;
    private static String pathSeparatorSymbol;
    private static int port;

    public static void init() {
        sshConfigFile = Config.getInstance().getSftpConfig().getSshConfigFile();
        authType = Config.getInstance().getSftpConfig().getAuthType();
        address = Config.getInstance().getSftpConfig().getAddress();
        username = Config.getInstance().getSftpConfig().getUsername();
        password = Config.getInstance().getSftpConfig().getPassword();
        backupsFolder = Config.getInstance().getSftpConfig().getBackupsFolder();
        keyFilePath = Config.getInstance().getSftpConfig().getKeyFilePath();
        useKnownHostsFile = Config.getInstance().getSftpConfig().getUseKnownHostsFile();
        knownHostsFilePath = Config.getInstance().getSftpConfig().getKnownHostsFilePath();
        pathSeparatorSymbol = Config.getInstance().getSftpConfig().getPathSeparatorSymbol();
        port = Config.getInstance().getSftpConfig().getPort();
    }

    /***\
     * Checks if the SFTP server is available. Sends warning to the console if not
     */
    public static boolean checkConnection() {
        return checkConnection(null);
    }

    /***\
     * Checks if the SFTP server is available. Sends warning to the console and sender if not
     */
    public static boolean checkConnection(CommandSender sender) {

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            Backuper.getInstance().getLogManager().warn("FTP(S) storage is disabled in config.yml", sender);
            return false;
        }

        Pair<Session, ChannelSftp> channelSftp;
        try {
            channelSftp = createChannel();
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the SFTP server", sender);
            return false;
        }
        if (channelSftp == null) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the SFTP server", sender);
            return false;
        }

        channelSftp.getLeft().disconnect();
        channelSftp.getRight().exit();

        return true;
    }

    @NotNull
    public static Pair<Session, ChannelSftp> createChannel() throws AuthenticationException {

        if (!authType.equals("password") && !authType.equals("key") && !authType.equals("key_pass")) {
            throw new AuthenticationException("Wrong auth type \"%s\"".formatted(authType));
        }

        JSch jsch = new JSch();
        Session sshSession = null;
        ChannelSftp sftpChannel = null;

        if (!sshConfigFile.isEmpty()) {
            try {
                jsch.setConfigRepository(OpenSSHConfig.parseFile(sshConfigFile));
            } catch (Exception e) {
                throw new AuthenticationException("Failed to load ssh config specified in config sftp.auth.sshConfigFile");
            }
        } else {
            try {
                if (authType.equals("key")) {
                    jsch.addIdentity(keyFilePath);
                }
                if (authType.equals("key_pass")) {
                    jsch.addIdentity(keyFilePath, password);
                }

                sshSession = jsch.getSession(username, address, port);

                if (authType.equals("password")) {
                    sshSession.setPassword(password);
                }

                Properties config = new Properties();
                if (useKnownHostsFile.equals("false")) {
                    config.put("StrictHostKeyChecking", "no");
                } else {
                    config.put("StrictHostKeyChecking", "yes");
                }
                sshSession.setConfig(config);

                if (!useKnownHostsFile.equals("false")) {
                    jsch.setKnownHosts(knownHostsFilePath);
                }

                sshSession.connect(15000);
                sftpChannel = (ChannelSftp) sshSession.openChannel("sftp");
                sftpChannel.connect(15000);

                return Pair.of(sshSession, sftpChannel);

            } catch (Exception exception) {
                try {
                    sftpChannel.exit();
                } catch (Exception ignored) {
                    // An error only occurres if the channel is null, so we don't need to handle it
                }
                try {
                    sshSession.disconnect();
                } catch (Exception ignored) {
                    // An error only occurres if the ssh session is null, so we don't need to handle it
                }
                throw new AuthenticationException("Failed to establish SFTP connection", exception);
            }
        }
        throw new AuthenticationException("Failed to establish SFTP connection");
    }

    public static void createFolder(String remoteFolderPath) throws SftpException {
        Pair<Session, ChannelSftp> sessionChannelSftpPair;
        try {
            sessionChannelSftpPair = createChannel();
        } catch (AuthenticationException e) {
            throw new RuntimeException("Failed to create folder on SFTP server", e);
        }
        Session session = sessionChannelSftpPair.getLeft();
        ChannelSftp sftpChannel = sessionChannelSftpPair.getRight();

        try {
            sftpChannel.mkdir(remoteFolderPath);
        } finally {
            sftpChannel.exit();
            session.disconnect();
        }
    }

    public static String resolve(String path, String fileName) {

        if (!path.endsWith(pathSeparatorSymbol)) {
            path += pathSeparatorSymbol;
        }
        return path + fileName;
    }

    public static void renameFile(String remoteFilePath, String newFilePath) throws SftpException {
        Pair<Session, ChannelSftp> sessionChannelSftpPair;
        try {
            sessionChannelSftpPair = createChannel();
        } catch (AuthenticationException e) {
            throw new RuntimeException("Failed to create folder on SFTP server", e);
        }
        Session session = sessionChannelSftpPair.getLeft();
        ChannelSftp sftpChannel = sessionChannelSftpPair.getRight();

        try {
            sftpChannel.rename(remoteFilePath, newFilePath);

        } finally {
            session.disconnect();
            sftpChannel.exit();
        }
    }

    public static ArrayList<String> ls(String remoteFolderPath) throws SftpException {
        Pair<Session, ChannelSftp> sessionChannelSftpPair;
        try {
            sessionChannelSftpPair = createChannel();
        } catch (AuthenticationException e) {
            throw new RuntimeException("Failed to create folder on SFTP server", e);
        }
        Session session = sessionChannelSftpPair.getLeft();
        ChannelSftp sftpChannel = sessionChannelSftpPair.getRight();

        try {
            Vector<ChannelSftp.LsEntry> ls = sftpChannel.ls(remoteFolderPath);
            ArrayList<String> files = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : ls) {
                files.add(entry.getFilename());
            }
            return files;

        } finally {
            sftpChannel.exit();
            session.disconnect();
        }
    }

    public static long getDirByteSize(String remoteFilePath) throws SftpException {
        Pair<Session, ChannelSftp> sessionChannelSftpPair;
        try {
            sessionChannelSftpPair = createChannel();
        } catch (AuthenticationException e) {
            throw new RuntimeException("Failed to create folder on SFTP server", e);
        }        Session session = sessionChannelSftpPair.getLeft();
        ChannelSftp sftpChannel = sessionChannelSftpPair.getRight();

        try {
            return getFileFolderByteSize(sftpChannel, remoteFilePath);
        } finally {
            session.disconnect();
            sftpChannel.exit();
        }
    }

    private static long getFileFolderByteSize(ChannelSftp sftpChannel, String remoteFilePath) throws SftpException {

        long dirSize = 0;
        if (!sftpChannel.stat(remoteFilePath).isDir()) {
            dirSize += sftpChannel.stat(remoteFilePath).getSize();

        } else {
            for (ChannelSftp.LsEntry entry : sftpChannel.ls(remoteFilePath)) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                    continue;
                }
                dirSize += getFileFolderByteSize(sftpChannel, SftpUtils.resolve(remoteFilePath, entry.getFilename()));
            }
        }
        return dirSize;
    }
}
