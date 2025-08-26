package ru.dvdishka.backuper.backend.util;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;

import javax.security.sasl.AuthenticationException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FtpUtils {

    private static boolean enabled;
    private static String backupsFolder;
    private static String username;
    private static String address;
    private static String password;
    private static String pathSeparatorSymbol;
    private static int port;

    private static final ArrayList<FTPClient> ftps = new ArrayList<>();

    public static void init() {
        enabled = Config.getInstance().getFtpConfig().isEnabled();
        backupsFolder = Config.getInstance().getFtpConfig().getBackupsFolder();
        username = Config.getInstance().getFtpConfig().getUsername();
        address = Config.getInstance().getFtpConfig().getAddress();
        password = Config.getInstance().getFtpConfig().getPassword();
        pathSeparatorSymbol = Config.getInstance().getFtpConfig().getPathSeparatorSymbol();
        port = Config.getInstance().getFtpConfig().getPort();
    }

    /***
     * Checks if the FTP(S) server is available. Sends warning to the console if not
     */
    public static boolean checkConnection() {
        return checkConnection(null);
    }

    /***
     * Checks if the FTP(S) server is available. Sends warning to the console and sender if not
     */
    public static boolean checkConnection(CommandSender sender) {
        try {
            if (!Config.getInstance().getFtpConfig().isEnabled()) {
                Backuper.getInstance().getLogManager().warn("FTP(S) server is disabled in config.yml", sender);
                return false;
            }

            getClient();
            return true;
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to establish connection to the FTP(S) server", sender);
            Backuper.getInstance().getLogManager().warn(e);
            return false;
        }
    }

    public static FTPClient getClient() throws IOException {
        FTPClient ftp = new FTPClient();

        // Enable FTP logging
        if (Config.getInstance().isBetterLogging()) {
            //ftp.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
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
            throw new AuthenticationException("Failed to establish FTP connection");
        }

        ftp.enterLocalPassiveMode();
        ftp.login(username, password);

        reply = ftp.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect();
            throw new AuthenticationException("Failed to establish FTP connection");
        }

        ftp.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
        ftp.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        ftp.setListHiddenFiles(true);

        ftps.add(ftp);

        return ftp;
    }

    public static List<String> ls(String path) throws IOException {

        FTPClient ftp = getClient();

        try {
            ftp.changeWorkingDirectory(path);
            FTPFile[] files = ftp.listFiles();
            ftp.changeWorkingDirectory("");
            return Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toCollection(ArrayList::new));
        } finally {
            ftp.disconnect();
        }
    }

    public static List<String> ls(FTPClient ftp, String path) throws IOException {

        try {
            ftp.changeWorkingDirectory(path);
            FTPFile[] files = ftp.listFiles();
            ftp.changeWorkingDirectory("");
            return Arrays.stream(files).map(FTPFile::getName).collect(Collectors.toCollection(ArrayList::new));

        } finally {
            ftp.disconnect();
        }
    }

    public static String resolve(String path, String fileName) {

        if (!path.endsWith(pathSeparatorSymbol)) {
            path = "%s%s".formatted(path, pathSeparatorSymbol);
        }
        return "%s%s".formatted(path, fileName);
    }

    public static long getDirByteSize(String remoteFilePath) throws IOException {

        FTPClient ftp = FtpUtils.getClient();
        try {
            return getDirByteSize(ftp, remoteFilePath);
        } finally {
            ftp.disconnect();
        }
    }

    private static long getDirByteSize(FTPClient ftp, String remoteFilePath) throws IOException {

        long dirSize = 0;
        ftp.changeWorkingDirectory("");
        FTPFile currentDir = ftp.mlistFile(remoteFilePath);

        if (currentDir.isFile()) {
            dirSize += currentDir.getSize();
        }
        if (currentDir.isDirectory()) {

            if (!ftp.changeWorkingDirectory(remoteFilePath)) {
                throw new RemoteException("Failed to get file list from FTP(S) server");
            }
            FTPFile[] files = ftp.listFiles();

            for (FTPFile file : files) {
                if (file.getName().equals(".") || file.getName().equals("..")) {
                    continue;
                }
                dirSize += getDirByteSize(ftp, FtpUtils.resolve(remoteFilePath, file.getName()));
            }
        }
        ftp.changeWorkingDirectory("");
        return dirSize;
    }

    public static void createFolder(String remoteFolderPath) throws IOException {

        FTPClient ftp = getClient();
        try {
            ftp.mkd(remoteFolderPath);
        } finally {
            ftp.disconnect();
        }
    }

    public static void createFolder(FTPClient ftp, String remoteFolderPath) throws IOException {

        try {
            ftp.mkd(remoteFolderPath);
        } finally {
            ftp.disconnect();
        }
    }

    public static void renameFile(String path, String newPath) throws IOException {

        FTPClient ftp = getClient();
        try {
            ftp.rename(path, newPath);
        } finally {
            ftp.disconnect();
        }
    }

    public static void renameFile(FTPClient ftp, String path, String newPath) throws IOException {

        try {
            ftp.rename(path, newPath);
        } finally {
            ftp.disconnect();
        }
    }
}
