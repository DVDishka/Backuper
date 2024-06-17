package ru.dvdishka.backuper.backend.utils;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.OpenSSHConfig;
import com.jcraft.jsch.Session;
import it.unimi.dsi.fastutil.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
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

    public static boolean checkConnection(CommandSender sender) {
        Pair<Session, ChannelSftp> channelSftp = createChannel(sender);
        boolean connected = channelSftp != null;

        try {
            channelSftp.first().disconnect();
        } catch (Exception ignored) {}
        try {
            channelSftp.second().exit();
        } catch (Exception ignored) {}

        return connected;
    }

    public static Pair<Session, ChannelSftp> createChannel(CommandSender sender) {

        if (!authType.equals("password") && !authType.equals("key") && !authType.equals("key_pass")) {
            Logger.getLogger().warn("Failed to connect to SFTP server", sender);
            Logger.getLogger().warn("Wrong authType: \"" + authType + "\"", sender);
            return null;
        }

        Session sshSession = null;
        ChannelSftp sftpChannel = null;
        
        try {
            JSch jsch = new JSch();

            try {

                if (Objects.equals(sshConfigFile, "")) {
                    throw new RuntimeException();
                }

                jsch.setConfigRepository(OpenSSHConfig.parseFile(sshConfigFile));

            } catch (Exception e) {

                if (e instanceof IOException) {
                    Logger.getLogger().warn("Failed to load ssh config specified in config sftp.auth.sshConfigFile", sender);
                }

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

                Properties config = new java.util.Properties();
                if (useKnownHostsFile.equals("false")) {
                    config.put("StrictHostKeyChecking", "no");
                } else {
                    config.put("StrictHostKeyChecking", "yes");
                }
                sshSession.setConfig(config);

                if (!useKnownHostsFile.equals("false")) {
                    jsch.setKnownHosts(knownHostsFilePath);
                }
            }

            sshSession.connect(15000);
            sftpChannel = (ChannelSftp) sshSession.openChannel("sftp");

            return Pair.of(sshSession, sftpChannel);

        } catch (JSchException e) {

            try {
                sshSession.disconnect();
            } catch (Exception ignored) {}

            try {
                sftpChannel.exit();
            } catch (Exception ignored) {}

            Logger.getLogger().warn("Failed to connect to SFTP server", sender);
            Logger.getLogger().warn("SftpUtils; createSftpChannel", e);

            return null;
        }
    }

    public static void createFolder(String remoteFolderPath, CommandSender sender) {

        Pair<Session, ChannelSftp> sessionChannelSftpPair = createChannel(sender);

        Session session = sessionChannelSftpPair.first();
        ChannelSftp sftpChannel = sessionChannelSftpPair.second();

        if (sftpChannel == null) {
            return;
        }

        try {
            sftpChannel.connect(10000);
            sftpChannel.mkdir(remoteFolderPath);

            sftpChannel.exit();
            session.disconnect();

        } catch (Exception e) {

            try {
                session.disconnect();
            } catch (Exception ignored) {}

            try {
                sftpChannel.exit();
            } catch (Exception ignored) {}

            Logger.getLogger().warn("Failed to create remote folder + \"" + remoteFolderPath + "\"", sender);
            Logger.getLogger().warn("SftpUtils; createRemoteFolder", e);
        }
    }

    public static String resolve(String path, String fileName) {

        if (!path.endsWith(pathSeparatorSymbol)) {
            path += pathSeparatorSymbol;
        }
        return path + fileName;
    }

    public static void renameFile(String remoteFilePath, String newFilePath, CommandSender sender) {

        Pair<Session, ChannelSftp> sessionChannelSftpPair = createChannel(sender);

        Session session = sessionChannelSftpPair.first();
        ChannelSftp sftpChannel = sessionChannelSftpPair.second();

        if (sftpChannel == null) {
            return;
        }

        try {
            sftpChannel.connect(10000);
            sftpChannel.rename(remoteFilePath, newFilePath);

            sftpChannel.exit();
            session.disconnect();

        } catch (Exception e) {

            try {
                session.disconnect();
            } catch (Exception ignored) {}

            try {
                sftpChannel.exit();
            } catch (Exception ignored) {}

            Logger.getLogger().warn("Failed to rename \"" + remoteFilePath + "\" to \"" + newFilePath + "\"", sender);
            Logger.getLogger().warn("SftpUtils; renameRemoteFolder", e);
        }
    }

    public static ArrayList<String> ls(String remoteFolderPath, CommandSender sender) {

        Pair<Session, ChannelSftp> sessionChannelSftpPair = createChannel(sender);

        Session session = sessionChannelSftpPair.first();
        ChannelSftp sftpChannel = sessionChannelSftpPair.second();

        if (sftpChannel == null) {
            return null;
        }

        try {
            sftpChannel.connect(10000);
            Vector<ChannelSftp.LsEntry> ls = sftpChannel.ls(remoteFolderPath);

            ArrayList<String> files = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : ls) {
                files.add(entry.getFilename());
            }

            sftpChannel.exit();
            session.disconnect();

            return files;

        } catch (Exception e) {

            try {
                session.disconnect();
            } catch (Exception ignored) {}

            try {
                sftpChannel.exit();
            } catch (Exception ignored) {}

            Logger.getLogger().warn("Failed to get file list from folder \"" + remoteFolderPath + "\"", sender);
            Logger.getLogger().warn("SftpUtils; ls", e);

            return null;
        }
    }

    public static long getDirByteSize(String remoteFilePath, CommandSender sender) {

        Pair<Session, ChannelSftp> sessionChannelSftpPair = createChannel(sender);

        Session session = sessionChannelSftpPair.first();
        ChannelSftp sftpChannel = sessionChannelSftpPair.second();

        if (sftpChannel == null) {
            return 0;
        }

        try {
            sftpChannel.connect(10000);

            long size = getFileFolderByteSize(sftpChannel, remoteFilePath, sender);

            sftpChannel.exit();
            session.disconnect();

            return size;

        } catch (Exception e) {

            try {
                session.disconnect();
            } catch (Exception ignored) {}

            try {
                sftpChannel.exit();
            } catch (Exception ignored) {}

            Logger.getLogger().warn("Failed to get dir size \"" + remoteFilePath + "\"", sender);
            Logger.getLogger().warn("SftpUtils; getDirByteSize", e);

            return 0;
        }
    }

    private static long getFileFolderByteSize(ChannelSftp sftpChannel, String remoteFilePath, CommandSender sender) {

        long dirSize = 0;

        try {
            if (!sftpChannel.stat(remoteFilePath).isDir()) {
                dirSize += sftpChannel.stat(remoteFilePath).getSize();

            } else {

                for (ChannelSftp.LsEntry entry : sftpChannel.ls(remoteFilePath)) {
                    if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                        continue;
                    }
                    dirSize += getFileFolderByteSize(sftpChannel, SftpUtils.resolve(remoteFilePath, entry.getFilename()), sender);
                }
            }
        } catch (com.jcraft.jsch.SftpException e) {
            Logger.getLogger().warn("Failed to get dir size \"" + remoteFilePath + "\"", sender);
            Logger.getLogger().warn("SftpUtils; getFileFolderByteSize", e);
        }
        return dirSize;
    }
}
