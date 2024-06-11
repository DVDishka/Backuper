package ru.dvdishka.backuper.backend.tasks.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.FtpUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Objects;

public class FtpGetFileFolderTask extends Task {

    private static String taskName = "FtpGetFileFolder";

    private String remotePathToGet = "";
    private File localTargetPathFile;
    private boolean createRootDirInTargetDir = true;

    FTPClient ftp;

    protected FtpGetFileFolderTask(String remotePathToGet, File localTargetPathFile, boolean createRootDirInTargetDir,
                                   boolean setLocked, CommandSender sender) {
        super(taskName, setLocked, sender);

        this.remotePathToGet = remotePathToGet;
        this.localTargetPathFile = localTargetPathFile;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    public void run() {

        try {

            if (setLocked) {
                Backuper.lock(this);
            }

            if (!isTaskPrepared) {
                prepareTask();
            }

            ftp = FtpUtils.createChannel(sender);

            if (ftp == null) {
                return;
            }

            if (createRootDirInTargetDir) {

                String remoteDirName = "";
                for (char c : remotePathToGet.toCharArray()) {

                    String symbol = String.valueOf(c);

                    if (Objects.equals(symbol, Config.getInstance().getSftpConfig().getPathSeparatorSymbol())) {
                        remoteDirName = "";
                    } else {
                        remoteDirName += symbol;
                    }
                }

                localTargetPathFile = localTargetPathFile.toPath().resolve(remoteDirName).toFile();
            }

            getFileFolder(remotePathToGet, localTargetPathFile, sender);

        } catch (Exception e) {

            Logger.getLogger().warn("Something went wrong when trying to download file/folder from FTP(S) server", sender);
            Logger.getLogger().warn("FtpGetFileFolderTask:run", e);

        } finally {

            if (setLocked) {
                Backuper.unlock();
                UIUtils.successSound(sender);
            }
            try {
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void prepareTask() {
        isTaskPrepared = true;

        maxProgress = FtpUtils.getDirByteSize(remotePathToGet, sender);
    }

    private void getFileFolder(String remoteDir, File localDir, CommandSender sender) {

        try {

            FTPFile currentDir = ftp.mlistFile(remoteDir);

            if (currentDir.isFile()) {

                localDir.createNewFile();

                try (FileOutputStream outputStream = new FileOutputStream(localDir)) {
                    ftp.retrieveFile(remoteDir, outputStream);

                } catch (Exception e) {
                    Logger.getLogger().warn("Failed to download file \"" + remoteDir + "\" from FTP(S) server");
                    Logger.getLogger().warn("FtpGetFileFolder:getFileFolder", e);
                }
                currentProgress += currentDir.getSize();
            }

            if (currentDir.isDirectory()) {

                ftp.mkd(remoteDir);
                localDir.mkdirs();

                for (FTPFile file : ftp.listFiles()) {
                    getFileFolder(FtpUtils.resolve(remoteDir, file.getName()),
                            localDir.toPath().resolve(file.getName()).toFile(), sender);
                }
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Something went wrong when trying to download file/folder from FTP(S) server", sender);
            Logger.getLogger().warn("FtpGetFileFolderTask:getFileFolder", e);
        }
    }
}