package ru.dvdishka.backuper.backend.task;

import com.jcraft.jsch.*;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.SftpUtils;

import javax.security.sasl.AuthenticationException;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class SftpGetDirTask extends BaseAsyncTask {

    private final String remotePathToGet;
    private File localTargetPathFile;
    private final boolean createRootDirInTargetDir;

    private Session session = null;
    private ChannelSftp sftpChannel = null;
    private long dirSize = 0;
    private final ArrayList<CompletableFuture<Void>> sftpTasks = new ArrayList<>();
    private final ArrayList<ru.dvdishka.backuper.backend.task.SftpProgressMonitor> progressMonitors = new ArrayList<>();

    public SftpGetDirTask(String remotePathToGet, File localTargetPathFile, boolean createRootDirInTargetDir) {
        super();

        this.remotePathToGet = remotePathToGet;
        this.localTargetPathFile = localTargetPathFile;
        this.createRootDirInTargetDir = createRootDirInTargetDir;
    }

    @Override
    protected void run() throws JSchException {

        try {
            if (!cancelled) {
                Pair<Session, ChannelSftp> sessionChannelSftpPair;
                try {
                    sessionChannelSftpPair = SftpUtils.createChannel();
                } catch (AuthenticationException e) {
                    throw new RuntimeException(e);
                }

                session = sessionChannelSftpPair.getLeft();
                sftpChannel = sessionChannelSftpPair.getRight();

                sftpChannel.connect(10000);
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

            if (!cancelled) {
                getRemoteDir(remotePathToGet, localTargetPathFile);
            }

        } finally {
            sftpChannel.exit();
            session.disconnect();
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) throws SftpException {
        dirSize = SftpUtils.getDirByteSize(remotePathToGet);
    }

    private void getRemoteDir(String remotePath, File localPath) {

        if (cancelled) {
            return;
        }

        try {
            SftpATTRS remoteAttrs = sftpChannel.stat(remotePath);

            if (!remoteAttrs.isDir()) {

                localPath.createNewFile();
                SftpProgressMonitor progressMonitor = new SftpProgressMonitor();
                progressMonitors.add(progressMonitor);

                CompletableFuture<Void> task = Backuper.getInstance().getScheduleManager().runAsync(() -> {
                    try {
                        sftpChannel.get(remotePath, localPath.getCanonicalPath(), progressMonitor);
                    } catch (Exception e) {
                        devWarn("Something went wrong when trying to download file \"" + remotePath + "\" from SFTP server");
                        devWarn(Arrays.toString(e.getStackTrace()));
                    }
                });

                sftpTasks.add(task);
                try {
                    task.join();
                } catch (Exception e) {
                    if (!cancelled) {
                        warn("Something went wrong when trying to download file \"" + remotePath + "\" from SFTP server", sender);
                        warn(e);
                    }
                }

            } else {

                localPath.mkdirs();
                for (ChannelSftp.LsEntry entry : sftpChannel.ls(remotePath)) {
                    if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                        continue;
                    }

                    getRemoteDir(SftpUtils.resolve(remotePath, entry.getFilename()), localPath.toPath().resolve(entry.getFilename()).toFile());
                }
            }

        } catch (Exception e) {
            warn("Failed to get file %s through the SFTP channel".formatted(remotePath), sender);
            warn(e);
        }
    }

    @Override
    public long getTaskCurrentProgress() {

        if (cancelled) {
            return dirSize;
        }

        long currentProgress = 0;

        for (SftpProgressMonitor progressMonitor : progressMonitors) {
            currentProgress += progressMonitor.getCurrentProgress();
        }

        return currentProgress;
    }

    @Override
    public long getTaskMaxProgress() {
        return dirSize;
    }

    @Override
    protected void cancel() {
        cancelled = true;

        for (CompletableFuture<Void> task : sftpTasks) {
            task.cancel(true);
        }
    }
}
