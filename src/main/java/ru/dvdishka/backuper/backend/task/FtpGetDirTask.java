package ru.dvdishka.backuper.backend.task;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.FtpUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class FtpGetDirTask extends BaseAsyncTask {

    private final String remotePathToGet;
    private File localTargetPathFile;
    private final boolean createRootDirInTargetDir;
    private final ArrayList<CompletableFuture<Void>> ftpTasks = new ArrayList<>();

    FTPClient ftp;

    public FtpGetDirTask(String remotePathToGet, File localTargetPathFile, boolean createRootDirInTargetDir) {
        super();

        this.remotePathToGet = remotePathToGet;
        this.localTargetPathFile = localTargetPathFile;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    protected void run() throws IOException {
        try {
            if (!cancelled) {
                ftp = FtpUtils.getClient();
            }

            if (createRootDirInTargetDir) {

                StringBuilder remoteDirName = new StringBuilder();
                for (char c : remotePathToGet.toCharArray()) {

                    String symbol = String.valueOf(c);

                    if (Objects.equals(symbol, Config.getInstance().getSftpConfig().getPathSeparatorSymbol())) {
                        remoteDirName = new StringBuilder();
                    } else {
                        remoteDirName.append(symbol);
                    }
                }

                localTargetPathFile = localTargetPathFile.toPath().resolve(remoteDirName.toString()).toFile();
            }

            if (!cancelled) {
                getFileFolder(remotePathToGet, localTargetPathFile, sender);
            }

        } finally {
            ftp.disconnect();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) throws IOException {
        maxProgress = FtpUtils.getDirByteSize(remotePathToGet);
    }

    private void getFileFolder(String remoteDir, File localDir, CommandSender sender) {

        if (cancelled) {
            return;
        }

        try {

            ftp.changeWorkingDirectory("");
            FTPFile currentDir = ftp.mlistFile(remoteDir);

            if (currentDir.isFile()) {

                localDir.createNewFile();

                CompletableFuture<Void> ftpTask = Backuper.getInstance().getScheduleManager().runAsync(() -> {

                    try (FileOutputStream outputStream = new FileOutputStream(localDir)) {

                        ftp.retrieveFile(remoteDir, outputStream);

                    } catch (Exception e) {
                        devWarn("Failed to download file \"" + remoteDir + "\" from FTP(S) server");
                        devWarn(Arrays.toString(e.getStackTrace()));
                    }
                    incrementCurrentProgress(currentDir.getSize());
                });

                ftpTasks.add(ftpTask);
                try {
                    ftpTask.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Failed to download file \"" + remoteDir + "\" from FTP(S) server", sender);
                        warn(e);
                    }
                }
            }

            if (currentDir.isDirectory()) {

                ftp.mkd(remoteDir);
                localDir.mkdirs();

                ftp.changeWorkingDirectory(remoteDir);

                for (FTPFile file : ftp.listFiles()) {
                    if (file.getName().equals(".") || file.getName().equals("..")) {
                        continue;
                    }
                    getFileFolder(FtpUtils.resolve(remoteDir, file.getName()),
                            localDir.toPath().resolve(file.getName()).toFile(), sender);
                }
            }

        } catch (Exception e) {
            warn("Something went wrong when trying to download file/folder from FTP(S) server", sender);
            warn(e);
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : ftpTasks) {
            task.cancel(true);
        }
    }
}
