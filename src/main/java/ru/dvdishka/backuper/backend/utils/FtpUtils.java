package ru.dvdishka.backuper.backend.utils;

import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.IOException;
import java.io.PrintWriter;
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

    private static ArrayList<FTPClient> ftps = new ArrayList<>();

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

        if (!Config.getInstance().getFtpConfig().isEnabled()) {
            return false;
        }

        try {
            FTPClient ftp = getClient(sender);
            boolean connected = ftp != null;
            ftp.disconnect();
            return connected;

        } catch (Exception e) {

            Logger.getLogger().warn("FtpUtils; checkConnection", e);
            return false;
        }
    }

    public static FTPClient getClient(CommandSender sender) {

        try {

            int cnt = 0;
            for (FTPClient client : ftps) {
                if (client.isConnected()) {
                    cnt++;
                }
            }

            FTPClient ftp = new FTPClient();

            if (Config.getInstance().isBetterLogging()) {
                ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
            }

            ftp.setConnectTimeout(10 * 1000);
            ftp.setDefaultTimeout(30 * 1000);
            ftp.setDataTimeout(30 * 1000);
            ftp.setControlKeepAliveTimeout(30 * 1000);
            ftp.setControlEncoding("UTF-8");

            ftp.connect(address, port);
            int reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                throw new IOException("Exception in connecting to FTP Server");
            }

            ftp.enterLocalPassiveMode();
            ftp.login(username, password);

            reply = ftp.getReplyCode();

            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                throw new IOException("Failed to login FTP Server");
            }

            ftp.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
            ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
            ftp.setListHiddenFiles(true);

            ftps.add(ftp);

            return ftp;

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to connect to FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils:createFtpChannel", e);
            return null;
        }
    }

    public static ArrayList<String> ls(String path, CommandSender sender) {

        FTPClient ftp = getClient(sender);

        try {
            ftp.changeWorkingDirectory(path);
            FTPFile[] files = ftp.listFiles();
            ftp.changeWorkingDirectory("");
            return new ArrayList<>(Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toList()));

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to get file list from FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils:ls", e);
            return null;

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    public static ArrayList<String> ls(FTPClient ftp, String path, CommandSender sender) {

        try {
            ftp.changeWorkingDirectory(path);
            FTPFile[] files = ftp.listFiles();
            ftp.changeWorkingDirectory("");
            return new ArrayList<>(Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toList()));

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to get file list from FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils:ls", e);
            return null;

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    public static String resolve(String path, String fileName) {

        if (!path.endsWith(pathSeparatorSymbol)) {
            path += pathSeparatorSymbol;
        }
        return path + fileName;
    }

    public static long getDirByteSize(String remoteFilePath, CommandSender sender) {

        FTPClient ftp = FtpUtils.getClient(sender);

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
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    public static long getDirByteSize(FTPClient ftp, String remoteFilePath, CommandSender sender) {

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
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    private static long getFileFolderByteSize(FTPClient ftp, String remoteFilePath, CommandSender sender) {

        long dirSize = 0;

        try {

            ftp.changeWorkingDirectory("");
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
            ftp.changeWorkingDirectory("");
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to get dir size \"" + remoteFilePath + "\" from FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils; getFileFolderByteSize", e);
        }
        return dirSize;
    }

    public static void createFolder(String remoteFolderPath, CommandSender sender) {

        FTPClient ftp = getClient(sender);

        try {
            ftp.mkd(remoteFolderPath);

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to create remote folder + \"" + remoteFolderPath + "\" on FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils; createRemoteFolder", e);

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    public static void createFolder(FTPClient ftp, String remoteFolderPath, CommandSender sender) {

        try {
            ftp.mkd(remoteFolderPath);

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to create remote folder + \"" + remoteFolderPath + "\" on FTP(S) server", sender);
            Logger.getLogger().warn("FtpUtils; createRemoteFolder", e);

        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    public static void renameFile(String path, String newPath, CommandSender sender) {

        FTPClient ftp = getClient(sender);
        try {
            ftp.rename(path, newPath);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to rename file on FTP(S) server \"" + path + "\" to \"" + newPath + "\"", sender);
            Logger.getLogger().warn("FtpUtils; renameFile", e);
        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }

    public static void renameFile(FTPClient ftp, String path, String newPath, CommandSender sender) {

        try {
            ftp.rename(path, newPath);
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to rename file on FTP(S) server \"" + path + "\" to \"" + newPath + "\"", sender);
            Logger.getLogger().warn("FtpUtils; renameFile", e);
        } finally {
            try {
                ftp.disconnect();
            } catch (Exception e) {
                Logger.getLogger().warn("FtpUtils; renameFile", e);
            }
        }
    }
}
