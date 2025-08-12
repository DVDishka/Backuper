package ru.dvdishka.backuper.backend.task;

import org.apache.commons.net.ftp.FTPClient;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.util.FtpUtils;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class FtpSendDirTask extends BaseAsyncTask {

    private final File localDirToSend;
    private final boolean createRootDirInTargetDir;
    private String remoteTargetDir;
    private final boolean forceExcludedDirs;
    private final ArrayList<CompletableFuture<Void>> ftpTasks = new ArrayList<>();

    private FTPClient ftp;

    public FtpSendDirTask(File localDirToSend, String remoteTargetDir, boolean createRootDirInTargetDir, boolean forceExcludedDirs) {
        super();

        this.localDirToSend = localDirToSend;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
        this.remoteTargetDir = remoteTargetDir;
        this.forceExcludedDirs = forceExcludedDirs;
    }

    @Override
    protected void run() throws IOException {

        try {

            if (!cancelled) {
                ftp = FtpUtils.getClient();
                if (ftp == null) {
                    return;
                }
            }

            if (createRootDirInTargetDir) {
                remoteTargetDir = FtpUtils.resolve(remoteTargetDir, localDirToSend.getName());
            }

            if (!cancelled) {
                sendFolder(localDirToSend, remoteTargetDir);
            }

        } finally {
            ftp.disconnect();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {
        if (forceExcludedDirs) {
            maxProgress = Utils.getFileFolderByteSize(localDirToSend);
        } else {
            maxProgress = Utils.getFileFolderByteSizeExceptExcluded(localDirToSend);
        }
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : ftpTasks) {
            task.cancel(true);
        }
    }

    private void sendFolder(File localDirToSend, String remotePath) {

        if (cancelled) {
            return;
        }

        if (!localDirToSend.exists()) {
            warn("Something went wrong while trying to send files from " + localDirToSend.getAbsolutePath());
            warn("Directory " + localDirToSend.getAbsolutePath() + " does not exist", sender);
            return;
        }

        {
            boolean isExcludedDirectory = Utils.isExcludedDirectory(localDirToSend, sender);

            if (isExcludedDirectory && !forceExcludedDirs) {
                return;
            }
        }

        if (localDirToSend.isFile() && !localDirToSend.getName().equals("session.lock")) {

            CompletableFuture<Void> ftpTask = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                try (InputStream inputStream = new FileInputStream(localDirToSend)) {

                    if (!ftp.storeFile(remotePath, inputStream)) {
                        throw new IOException("Failed to send file \"" + localDirToSend.getCanonicalPath() + "\" to \"" + remotePath + "\"");
                    }
                    incrementCurrentProgress(localDirToSend.length());

                } catch (Exception e) {
                    devWarn("Something went wrong while sending file \"" + localDirToSend.getPath() + "\" to FTP(S) server");
                    devWarn(Arrays.toString(e.getStackTrace()));
                }
            });
            ftpTasks.add(ftpTask);
            try {
                ftpTask.join();
            } catch (Exception e) {
                if (!cancelled) {
                    warn("Something went wrong while sending file \"" + localDirToSend.getPath() + "\" to FTP(S) server", sender);
                    warn(e);
                }
            }
        }
        if (localDirToSend.isDirectory() && localDirToSend.listFiles() != null) {

            try {
                ftp.makeDirectory(remotePath);
            } catch (Exception ignored) {
            }

            for (File file : localDirToSend.listFiles()) {
                sendFolder(file, FtpUtils.resolve(remotePath, file.getName()));
            }
        }
    }
}
