package ru.dvdishka.backuper.backend.utils;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FtpUtils {

    private static boolean enabled;
    private static String backupsFolder;
    private static String username;
    private static String address;
    private static String password;
    private static String pathSeparatorSymbol;
    private static int port;

    public static void init() {
        enabled = Config.getInstance().getFtpConfig().isEnabled();
        backupsFolder = Config.getInstance().getFtpConfig().getBackupsFolder();
        username = Config.getInstance().getFtpConfig().getUsername();
        address = Config.getInstance().getFtpConfig().getAddress();
        password = Config.getInstance().getFtpConfig().getPassword();
        pathSeparatorSymbol = Config.getInstance().getFtpConfig().getPathSeparatorSymbol();
        port = Config.getInstance().getFtpConfig().getPort();
    }

    public static boolean checkConnection(CommandSender sender) {
        return createChannel(sender) != null;
    }

    public static FTPClient createChannel(CommandSender sender) {

        try {
            FTPClient ftp = new FTPClient();
            ftp.setControlEncoding("UTF-8");
            ftp.connect(address, port);
            int reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                throw new IOException("Exception in connecting to FTP Server");
            }

            ftp.enterLocalPassiveMode();
            ftp.login(username, password);

            ftp.setListHiddenFiles(true);

            return ftp;

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to connect to FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils:createFtpChannel", e);
            return null;
        }
    }

    public static ArrayList<String> ls(String path, CommandSender sender) {

        FTPClient ftp = createChannel(sender);

        try {
            ftp.changeWorkingDirectory(path);
            FTPFile[] files = ftp.listFiles();
            return new ArrayList<>(Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toList()));

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to get file list from FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils:ls", e);
            return null;

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public static String resolve(String path, String fileName) {

        if (!path.endsWith(pathSeparatorSymbol)) {
            path += pathSeparatorSymbol;
        }
        return path + fileName;
    }

    public static long getDirByteSize(String remoteFilePath, CommandSender sender) {

        FTPClient ftp = FtpUtils.createChannel(sender);

        if (ftp == null) {
            return 0;
        }

        try {

            long size = getFileFolderByteSize(ftp, remoteFilePath, sender);

            return size;

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to get dir size \"" + remoteFilePath + "\" from FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils; getDirByteSize", e);

            return 0;

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    private static long getFileFolderByteSize(FTPClient ftp, String remoteFilePath, CommandSender sender) {

        long dirSize = 0;

        try {

            FTPFile currentDir = ftp.mlistFile(remoteFilePath);

            if (currentDir.isFile()) {
                dirSize += currentDir.getSize();

            }
            if (currentDir.isDirectory()) {

                boolean dr = ftp.changeWorkingDirectory(remoteFilePath);
                FTPFile[] files = ftp.listFiles();

                if (!dr) {
                    Logger.getLogger().warn("Failed to get file list from FTP(S) server", sender);
                    return 0;
                }

                for (FTPFile file : files) {
                    if (file.getName().equals(".") || file.getName().equals("..")) {
                        continue;
                    }
                    dirSize += getFileFolderByteSize(ftp, FtpUtils.resolve(remoteFilePath, file.getName()), sender);
                }
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get dir size \"" + remoteFilePath + "\" from FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils; getFileFolderByteSize", e);
        }
        return dirSize;
    }

    public static void createFolder(String remoteFolderPath, CommandSender sender) {

        FTPClient ftp = createChannel(sender);

        try {
            ftp.mkd(remoteFolderPath);

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to create remote folder + \"" + remoteFolderPath + "\" on FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils; createRemoteFolder", e);

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public static void renameFile(String path, String newPath, CommandSender sender) {

        FTPClient ftp = createChannel(sender);
        try {
            ftp.rename(path, newPath);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to rename file on FTP(S) server \"" + path + "\" to \"" + newPath + "\"", sender);
            Logger.getLogger().warn("FtpUtils; renameFile", e);
        }
    }
}
